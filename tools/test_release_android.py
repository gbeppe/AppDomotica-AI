"""Offline tests for tools/release_android.py and tools/publish_android_build.py.

Run from the repository root: python3 -B -m unittest discover -s tools -p 'test_*.py'
"""
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import unittest.mock
import zipfile
from datetime import datetime, timezone
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))
import publish_android_build as pub  # noqa: E402
import release_android as rel  # noqa: E402

SHA = '0123456789abcdef0123456789abcdef01234567'


def identity(build_id='bld_x', timestamp='2026-10-10T12:00:00Z'):
    return pub.validate_android_identity(SHA, 'clean', build_id, timestamp)


class IdentityAndConfigTest(unittest.TestCase):
    def test_identity_validation(self):
        self.assertEqual(identity().message()['schema'], 'domopi.build_identity.v1')
        for args in ((SHA[:39], 'clean', 'bld_x', 't'), (SHA, 'maybe', 'bld_x', 't'),
                     (SHA, 'clean', 'dev_1', 't'), (SHA, 'clean', 'bld_x', '')):
            with self.assertRaises(pub.AndroidPublishError):
                pub.validate_android_identity(*args)

    def test_config_requires_exact_fields_and_password(self):
        with tempfile.TemporaryDirectory() as tmp:
            cfg, pwd = Path(tmp, 'mqtt.json'), Path(tmp, 'pwd')
            cfg.write_text(json.dumps({'host': '192.168.1.20', 'port': 1883, 'username': 'u'}))
            pwd.write_text('secret\n')
            config = pub.load_mqtt_config(cfg, pwd)
            self.assertEqual((config.host, config.port, config.username, config.password),
                             ('192.168.1.20', 1883, 'u', 'secret'))
            self.assertNotIn('secret', repr(config))
            cfg.write_text(json.dumps({'host': 'h', 'port': 1883, 'username': 'u', 'password': 'p'}))
            with self.assertRaises(pub.AndroidPublishError):
                pub.load_mqtt_config(cfg, pwd)
            with self.assertRaises(pub.AndroidPublishError):
                pub.load_mqtt_config(Path(tmp, 'missing.json'), pwd)

    def test_retained_match_is_exact(self):
        expected = identity()
        good = json.dumps(expected.message())
        msg = lambda payload, retain=True, topic=pub.TOPIC: SimpleNamespace(topic=topic, retain=retain, payload=payload)
        self.assertTrue(pub.retained_identity_matches(msg(good), pub.TOPIC, expected))
        self.assertFalse(pub.retained_identity_matches(msg(good, retain=False), pub.TOPIC, expected))
        self.assertFalse(pub.retained_identity_matches(msg(good, topic='other'), pub.TOPIC, expected))
        self.assertFalse(pub.retained_identity_matches(msg(good[:-1] + ',"dirty":"clean"}'), pub.TOPIC, expected))
        other = json.dumps(identity(build_id='bld_y').message())
        self.assertFalse(pub.retained_identity_matches(msg(other), pub.TOPIC, expected))


class FakeClient:
    """Minimal paho stand-in: retained store shared across instances."""
    store = {}

    def __init__(self, *, replay=True, connect_rc=0):
        self.replay, self.connect_rc = replay, connect_rc
        self.on_connect = self.on_message = None
        self.subscribed = []

    def username_pw_set(self, user, password):
        self.user = user

    def connect(self, host, port, keepalive):
        self.target = (host, port)

    def loop_start(self):
        self.on_connect(self, None, None, self.connect_rc, None)
        return 0

    def publish(self, topic, payload, qos, retain):
        self.published = (topic, payload, qos, retain)
        FakeClient.store[topic] = payload
        return SimpleNamespace(wait_for_publish=lambda timeout: None, is_published=lambda: True)

    def subscribe(self, topic, qos):
        self.subscribed.append((topic, qos))
        if self.replay and topic in FakeClient.store:
            self.on_message(self, None, SimpleNamespace(topic=topic, retain=True, payload=FakeClient.store[topic]))
        return 0, 1

    def disconnect(self):
        pass

    def loop_stop(self):
        pass


class PublishTest(unittest.TestCase):
    config = pub.MqttConfig('192.168.1.20', 1883, 'u', 'p')

    def test_publish_is_qos2_retained_and_read_back(self):
        client = FakeClient()
        self.assertTrue(pub.publish_build_identity(self.config, identity(), client_factory=lambda: client, timeout=0.1))
        topic, payload, qos, retain = client.published
        self.assertEqual((topic, qos, retain), ('domopi/build/android', 2, True))
        self.assertEqual(json.loads(payload), identity().message())
        self.assertEqual(client.target, ('192.168.1.20', 1883))

    def test_missing_read_back_or_refused_connection_fails(self):
        self.assertFalse(pub.publish_build_identity(
            self.config, identity(), client_factory=lambda: FakeClient(replay=False), timeout=0.05))
        self.assertFalse(pub.publish_build_identity(
            self.config, identity(), client_factory=lambda: FakeClient(connect_rc=5), timeout=0.05))

    def test_republish_command_reads_record(self):
        with tempfile.TemporaryDirectory() as tmp:
            record = pub.save_identity_record(tmp, identity())
            seen = []
            code = pub.main(['republish', str(record)], publisher=lambda cfg, ident: seen.append(ident) or True,
                            config_loader=lambda c, p: self.config)
            self.assertEqual(code, 0)
            self.assertEqual(seen, [identity()])
            self.assertEqual(pub.main(['republish', str(Path(tmp, 'none.json'))],
                                      publisher=lambda c, i: True, config_loader=lambda c, p: self.config), 1)


class FakeRepo:
    """git/gradle stand-in: scripted outputs, APK produced by the fake Gradle."""

    def __init__(self, root, git_dir, *, dirty='', head=SHA, head_after=None, embed=True, gradle_rc=0):
        self.root, self.git_dir, self.dirty = root, git_dir, dirty
        self.heads = [head, head_after or head]
        self.embed, self.gradle_rc = embed, gradle_rc
        self.gradle_calls = []

    def run(self, args, cwd):
        out = {
            ('git', 'rev-parse', '--show-toplevel'): str(self.root),
            ('git', 'rev-parse', '--path-format=absolute', '--git-common-dir'): str(self.git_dir),
            ('git', 'status', '--porcelain=v1', '--untracked-files=all'): self.dirty,
        }.get(tuple(args))
        if tuple(args) == ('git', 'rev-parse', 'HEAD^{commit}'):
            out = self.heads.pop(0) if len(self.heads) > 1 else self.heads[0]
        return SimpleNamespace(returncode=0, stdout=out or '')

    def gradle(self, args, cwd):
        self.gradle_calls.append(args)
        if self.gradle_rc == 0:
            apk = Path(cwd, rel.APK)
            apk.parent.mkdir(parents=True, exist_ok=True)
            values = [a.split('=', 1)[1] for a in args if a.startswith('-Pdomopi.release.')]
            with zipfile.ZipFile(apk, 'w') as z:
                z.writestr('classes.dex', ('\x00'.join(values) if self.embed else 'dev').encode())
        return SimpleNamespace(returncode=self.gradle_rc)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name, 'repo')
        self.root.mkdir()
        self.git_dir = Path(self.tmp.name, 'canonical.git')
        self.published = []

    def tearDown(self):
        self.tmp.cleanup()

    def release(self, repo, **kw):
        out = io.StringIO()
        with unittest.mock.patch('sys.stdout', out):
            return rel.release(root=self.root, run=repo.run, gradle=repo.gradle,
                               publisher=lambda cfg, ident: self.published.append(ident) or kw.get('ok', True),
                               config_loader=lambda c, p: 'cfg', identifier=lambda: 'abc',
                               clock=lambda: datetime(2026, 10, 10, 12, tzinfo=timezone.utc),
                               canonical_git_dir=kw.get('canonical', self.git_dir))

    def test_full_flow_builds_records_and_publishes_exact_identity(self):
        repo = FakeRepo(self.root, self.git_dir)
        apk = self.release(repo)
        self.assertEqual(apk, self.root / rel.RECORDS / 'bld_abc.apk')
        self.assertEqual(repo.gradle_calls, [rel.gradle_command(identity('bld_abc', '2026-10-10T12:00:00Z'))])
        self.assertEqual(self.published, [identity('bld_abc', '2026-10-10T12:00:00Z')])
        record = json.loads((self.root / rel.RECORDS / 'bld_abc.json').read_text())
        self.assertEqual(record['identity']['build_id'], 'bld_abc')
        self.assertEqual(record['artifact_info']['sha256'], rel.sha256_file(apk))

    def test_refusals_never_publish(self):
        cases = [
            (dict(dirty=' M app/x.kt'), 'Clean committed source required'),
            (dict(head='nothex'), 'Invalid Git identity'),
            (dict(gradle_rc=1), 'Gradle build failed'),
            (dict(head_after='f' * 40), 'Source changed during the build'),
            (dict(embed=False), 'APK does not embed the release identity'),
        ]
        for kwargs, reason in cases:
            with self.subTest(reason=reason):
                with self.assertRaises(rel.ReleaseError) as ctx:
                    self.release(FakeRepo(self.root, self.git_dir, **kwargs))
                self.assertEqual(ctx.exception.args, (reason,))
                self.assertIn(reason, rel.SAFE_REASONS)
                self.assertEqual(self.published, [])

    def test_wrong_repository_and_failed_read_back(self):
        with self.assertRaises(rel.ReleaseError) as ctx:
            self.release(FakeRepo(self.root, self.git_dir), canonical=Path(self.tmp.name, 'other.git'))
        self.assertEqual(ctx.exception.args, ('Wrong repository',))
        with self.assertRaises(rel.ReleaseError) as ctx:
            self.release(FakeRepo(self.root, self.git_dir), ok=False)
        self.assertEqual(ctx.exception.args, ('Identity publication or read-back failed',))

    def test_existing_build_id_and_stale_apk_are_never_accepted(self):
        records = self.root / rel.RECORDS
        records.mkdir(parents=True)
        (records / 'bld_abc.json').write_text('{}')
        with self.assertRaises(rel.ReleaseError) as ctx:
            self.release(FakeRepo(self.root, self.git_dir))
        self.assertEqual(ctx.exception.args, ('Build identifier already exists',))
        (records / 'bld_abc.json').unlink()
        stale = self.root / rel.APK
        stale.parent.mkdir(parents=True, exist_ok=True)
        stale.write_text('old')
        with self.assertRaises(rel.ReleaseError):
            self.release(FakeRepo(self.root, self.git_dir, gradle_rc=0, embed=False))
        self.assertEqual(self.published, [])

    def test_diagnostics_print_only_allowlisted_text(self):
        self.assertEqual(rel.failure_diagnostic(rel.ReleaseError('Gradle build failed')),
                         'HOLD: Android release failed: Gradle build failed')
        self.assertEqual(rel.failure_diagnostic(rel.ReleaseError('password=hunter2')), rel.HOLD)
        self.assertEqual(rel.failure_diagnostic(RuntimeError('boom')), rel.HOLD)

    def test_main_logs_and_maps_outcomes(self):
        log = Path(self.tmp.name, 'release.log')
        with unittest.mock.patch('sys.stdout', io.StringIO()):
            self.assertEqual(rel.main(operation=lambda: None, log_path=log), 0)
            self.assertEqual(rel.main(operation=lambda: (_ for _ in ()).throw(rel.ReleaseError('Wrong repository')),
                                      log_path=log), 1)
        text = log.read_text()
        self.assertIn(rel.PASS, text)
        self.assertIn('HOLD: Android release failed: Wrong repository', text)

    def test_gradle_never_publishes(self):
        gradle = Path(__file__).resolve().parents[1].joinpath('app', 'build.gradle.kts').read_text()
        self.assertIn('domopi.release.gitSha', gradle)
        self.assertNotIn('publish_android_build', gradle)


if __name__ == '__main__':
    unittest.main()
