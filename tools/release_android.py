"""Operator-invoked DomoPi Android release on .40: build, record, publish; no install.

Ported from WallBox REL-APP.1. Only clean, committed sources are released; the APK
embeds the release identity and that same identity is published retained on
domopi/build/android, so the app's Build icon turns green only on this APK.
"""
import argparse
import contextlib
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parent))
import publish_android_build  # noqa: E402

CANONICAL_GIT_DIR = Path('/home/giuseppe/AndroidStudioProjects/DomoPiAndroidApp/.git')
APK = Path('app/build/outputs/apk/debug/app-debug.apk')
RECORDS = Path('work/android-builds')
PASS = 'PASS: Android release published; install the APK printed above on the phone'
HOLD = 'HOLD: Android release failed; reconcile the local log before retry'
INTERRUPTED = 'HOLD: Android release interrupted by operator; reconcile the local log before retry'

PHASES = (
    ('checks', 'Verifiche locali (repository, sorgenti pulite)'),
    ('build', 'Build APK debug con identità di release (Gradle)'),
    ('record', 'Record immutabile e copia dell APK'),
    ('publish', 'Pubblicazione identità su MQTT con rilettura esatta'),
)


class ReleaseError(RuntimeError):
    """Application-controlled failure; only allowlisted text is ever printed."""


SAFE_REASONS = frozenset({
    'Another release is already in progress',
    'APK does not embed the release identity',
    'Build identifier already exists',
    'Clean committed source required',
    'Gradle build failed',
    'Identity publication or read-back failed',
    'Invalid Git identity',
    'Local Git command failed',
    'MQTT configuration unavailable',
    'Invalid MQTT configuration',
    'Release APK missing',
    'Source changed during the build',
    'Wrong repository',
})


def run_command(args, cwd):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True)


def checked(run, args, cwd):
    result = run(args, cwd=cwd)
    if result.returncode:
        raise ReleaseError('Local Git command failed')
    return result.stdout.strip()


def gradle_command(identity):
    """assembleDebug with the release identity (debug signing, as in WallBox R-P1)."""
    return ['./gradlew', ':app:assembleDebug',
            '-Pdomopi.release.gitSha=' + identity.git_sha,
            '-Pdomopi.release.buildId=' + identity.build_id,
            '-Pdomopi.release.buildTimestamp=' + identity.build_timestamp]


def apk_embeds(apk_path, identity):
    """BuildConfig strings live in the dex files: the APK must carry this exact identity."""
    needles = [value.encode() for value in (identity.git_sha, identity.build_id, identity.build_timestamp)]
    found = set()
    with zipfile.ZipFile(apk_path) as apk:
        for name in apk.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                data = apk.read(name)
                found.update(needle for needle in needles if needle in data)
    return found == set(needles)


def sha256_file(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as handle:
        for block in iter(lambda: handle.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def clean_head(run, root):
    sha = checked(run, ['git', 'rev-parse', 'HEAD^{commit}'], root)
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ReleaseError('Invalid Git identity')
    if checked(run, ['git', 'status', '--porcelain=v1', '--untracked-files=all'], root):
        raise ReleaseError('Clean committed source required')
    return sha


def release(*, root=None, run=None, gradle=None, publisher=None, config_loader=None, identifier=None,
            clock=None, step=None, canonical_git_dir=CANONICAL_GIT_DIR):
    step = step or (lambda key: None)
    run = run or run_command

    step('checks')
    root = Path(checked(run, ['git', 'rev-parse', '--show-toplevel'], root or Path.cwd())).resolve()
    common = Path(checked(run, ['git', 'rev-parse', '--path-format=absolute', '--git-common-dir'], root))
    if common.resolve() != Path(canonical_git_dir).resolve():
        raise ReleaseError('Wrong repository')
    sha = clean_head(run, root)
    records = root / RECORDS
    records.mkdir(parents=True, exist_ok=True)
    with (records / '.release.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ReleaseError('Another release is already in progress') from None
        build_id = 'bld_' + str((identifier or uuid4)())
        timestamp = (clock or (lambda: datetime.now(timezone.utc)))().isoformat().replace('+00:00', 'Z')
        identity = publish_android_build.validate_android_identity(sha, 'clean', build_id, timestamp)
        record_path = records / (build_id + '.json')
        stored_apk = records / (build_id + '.apk')
        if record_path.exists() or stored_apk.exists():
            raise ReleaseError('Build identifier already exists')

        step('build')
        apk = root / APK
        if apk.exists():
            apk.unlink()  # never accept a stale APK from an earlier build
        result = (gradle or run)(gradle_command(identity), cwd=root)
        if result.returncode:
            raise ReleaseError('Gradle build failed')
        if not apk.is_file():
            raise ReleaseError('Release APK missing')
        if clean_head(run, root) != sha:
            raise ReleaseError('Source changed during the build')
        if not apk_embeds(apk, identity):
            raise ReleaseError('APK does not embed the release identity')

        step('record')
        shutil.copyfile(apk, stored_apk)
        os.chmod(stored_apk, 0o444)
        artifact = {'apk': str(stored_apk), 'sha256': sha256_file(stored_apk), 'signing': 'debug'}
        publish_android_build.save_identity_record(records, identity, artifact_info=artifact)
        os.chmod(record_path, 0o444)

        step('publish')
        overrides = {key: value for key, value in (('publisher', publisher), ('config_loader', config_loader))
                     if value is not None}
        try:
            published = publish_android_build.publish(identity, **overrides)
        except publish_android_build.AndroidPublishError as exc:
            raise ReleaseError(*exc.args) from None
        if not published:
            raise ReleaseError('Identity publication or read-back failed')
        print(f'APK: {stored_apk}')
        print(f'Build ID: {build_id}')
        return stored_apk


def failure_diagnostic(exc):
    if isinstance(exc, ReleaseError) and len(exc.args) == 1 and exc.args[0] in SAFE_REASONS:
        return 'HOLD: Android release failed: ' + exc.args[0]
    return HOLD


class Tee:
    def __init__(self, *streams):
        self.streams = streams

    def write(self, text):
        for stream in self.streams:
            stream.write(text)
        return len(text)

    def flush(self):
        for stream in self.streams:
            stream.flush()


def main(argv=None, *, operation=None, log_path=None):
    argparse.ArgumentParser(description=__doc__).parse_args([] if argv is None and operation is not None else argv)
    log_path = log_path or (Path(__file__).resolve().parents[1] / RECORDS / 'release.log')
    try:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        # Open before building; failure to record the operation must prevent publication.
        with log_path.open('a', encoding='utf-8') as log:
            os.chmod(log_path, 0o600)
            with contextlib.redirect_stdout(Tee(sys.stdout, log)):
                print(f'--- release {datetime.now(timezone.utc).isoformat()}')
                labels = dict(PHASES)
                try:
                    if operation is not None:
                        operation()
                    else:
                        release(step=lambda key: print(f'[{key}] {labels[key]}', flush=True))
                except KeyboardInterrupt:
                    print(INTERRUPTED)
                    return 130
                except Exception as exc:
                    print(failure_diagnostic(exc))
                    return 1
                print(PASS)
                return 0
    except Exception:
        print(HOLD)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
