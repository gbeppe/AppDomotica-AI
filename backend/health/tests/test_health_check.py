import collections
import json
import os
import sys
import tempfile
import unittest
from datetime import datetime
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
import health_check as h  # noqa: E402

Usage = collections.namedtuple('Usage', 'total used free')


def context(commands=None, **over):
    """Fake environment: commands maps the first words of a command to (code, output)."""
    commands = commands or {}

    def run(cmd, timeout=15):
        for key, value in commands.items():
            if ' '.join(cmd).startswith(key):
                return value
        return 0, ''
    ctx = {'run': run, 'tcp_open': lambda host, port: True, 'ping': lambda host: True,
           'http_alive': lambda url, insecure=False: True, 'broker_probe': lambda spec: True,
           'glob': lambda pattern: [pattern], 'mosquitto_settings': lambda paths: {},
           'disk_usage': lambda path: Usage(0, 0, 2000 * 1024 * 1024), 'mem_available_mb': lambda: 900,
           'now': lambda: 1_000_000.0, 'state': {}}
    ctx.update(over)
    return ctx


class CheckTests(unittest.TestCase):
    def test_power_undervoltage_now_is_urgent_and_events_are_counted(self):
        low = context({'vcgencmd get_throttled': (0, 'throttled=0x50005\n')})
        res = h.check_power({}, low)[0]
        self.assertFalse(res['ok'])
        self.assertTrue(res['urgent'])
        events = context({'vcgencmd get_throttled': (0, 'throttled=0x0\n'),
                          'journalctl -k': (0, 'Under-voltage detected!\n' * 4)})
        res = h.check_power({}, events)[0]
        self.assertFalse(res['ok'])
        self.assertIn('4 eventi', res['detail'])
        self.assertTrue(h.check_power({}, context({'vcgencmd get_throttled': (0, 'throttled=0x0\n')}))[0]['ok'])

    def test_services(self):
        ctx = context({'systemctl is-active mosquitto': (0, 'active\n'),
                       'systemctl is-active ntfy': (3, 'inactive\n')})
        out = h.check_services({'services': ['mosquitto', 'ntfy'], 'urgent_services': ['mosquitto']}, ctx)
        self.assertEqual([r['ok'] for r in out], [True, False])
        self.assertIn('systemctl restart ntfy', out[1]['advice'])

    def test_mosquitto_settings_per_listener(self):
        with tempfile.TemporaryDirectory() as folder:
            main = os.path.join(folder, 'mosquitto.conf')
            extra = os.path.join(folder, 'c.conf')
            open(main, 'w').write('persistence true\n# allow_anonymous false\nlistener 1883\nallow_anonymous true\n')
            open(extra, 'w').write('listener 1884 192.168.1.20\nallow_anonymous false\n')
            self.assertEqual(h.mosquitto_settings([main, extra]),
                             {'persistence': 'true', 'anonymous:1883': 'true', 'anonymous:1884': 'false'})
            open(main, 'w').write('persistence false\nallow_anonymous true\n')
            self.assertEqual(h.mosquitto_settings([main]), {'persistence': 'false', 'anonymous:default': 'true'})

    def test_mosquitto_config_differs(self):
        ctx = context(mosquitto_settings=lambda paths: {'persistence': 'false'})
        out = h.check_mosquitto_config({'mosquitto': {'files': ['x'], 'expect': {'persistence': 'true'}}}, ctx)
        self.assertFalse(out[0]['ok'])
        self.assertIn('persistence = false (atteso true)', out[0]['detail'])

    def test_tailscale_and_reachability(self):
        self.assertFalse(h.check_tailscale({}, context({'tailscale': (0, '{"BackendState": "Stopped"}')}))[0]['ok'])
        self.assertTrue(h.check_tailscale({}, context({'tailscale': (0, '{"BackendState": "Running"}')}))[0]['ok'])
        cfg = {'peer': {'name': '.15', 'ip': '192.168.1.15'}, 'router': '192.168.1.1'}
        out = h.check_reachability(cfg, context(tcp_open=lambda host, port: False))
        self.assertEqual([r['ok'] for r in out], [False, True])

    def test_resources_and_kernel_errors(self):
        ctx = context({'vcgencmd measure_temp': (0, "temp=80.1'C\n")},
                      disk_usage=lambda p: Usage(0, 0, 300 * 1024 * 1024), mem_available_mb=lambda: 50)
        out = {r['id']: r['ok'] for r in h.check_resources({}, ctx)}
        self.assertEqual(out, {'disk': False, 'memory': False, 'temperature': False})
        sd = h.check_kernel_errors({}, context({'journalctl -k': (0, 'mmc0: Timeout waiting for hardware cmd error\n')}))
        self.assertFalse(sd[0]['ok'])

    def test_energy_data_age_from_redis(self):
        cfg = {'emoncms_feeds': [{'name': 'Powerwall', 'feed': 305, 'max_age_s': 600},
                                 {'name': 'emonTx', 'feed': 24, 'max_age_s': 600}]}
        ctx = context({'redis-cli hget feed:305 time': (0, '999990\n'), 'redis-cli hget feed:24 time': (0, '998000\n')})
        out = h.check_energy_data(cfg, ctx)
        self.assertEqual([r['ok'] for r in out], [True, False])
        self.assertIn('2000 s fa', out[1]['detail'])

    def test_emontx_sign_needs_ten_opposite_readings(self):
        cfg = {'emontx_sign': {'site_feed': 305, 'import_feed': 24, 'export_feed': 27, 'min_w': 150, 'mismatches': 10}}
        state = {}
        opposite = {'redis-cli hget feed:305 value': (0, '-458\n'), 'redis-cli hget feed:24 value': (0, '544\n'),
                    'redis-cli hget feed:27 value': (0, '0\n')}
        for _ in range(9):
            self.assertTrue(h.check_emontx_sign(cfg, context(opposite, state=state))[0]['ok'])
        self.assertFalse(h.check_emontx_sign(cfg, context(opposite, state=state))[0]['ok'])
        same = dict(opposite, **{'redis-cli hget feed:24 value': (0, '0\n'), 'redis-cli hget feed:27 value': (0, '507\n')})
        self.assertTrue(h.check_emontx_sign(cfg, context(same, state=state))[0]['ok'])
        small = dict(opposite, **{'redis-cli hget feed:305 value': (0, '-40\n')})
        state['_emontx_sign']['mismatches'] = 5
        h.check_emontx_sign(cfg, context(small, state=state))
        self.assertEqual(state['_emontx_sign']['mismatches'], 5)  # low power: no evidence either way


class AlertTests(unittest.TestCase):
    cfg = {'confirm': {'peer': 3}, 'quiet_hours': [23, 7]}
    day = datetime(2026, 10, 23, 10, 0)
    night = datetime(2026, 10, 23, 2, 0)

    def fail(self, check_id='service:ntfy', urgent=False):
        return h.result(check_id, False, 'stato inactive', 'riavvia', urgent=urgent)

    def test_one_alert_then_reminder_after_six_hours_then_recovery(self):
        state = {}
        self.assertEqual([k for k, r in h.decide_alerts([self.fail()], state, 0, self.day, self.cfg)], ['fault'])
        self.assertEqual(h.decide_alerts([self.fail()], state, 120, self.day, self.cfg), [])
        self.assertEqual([k for k, r in h.decide_alerts([self.fail()], state, 6 * 3600, self.day, self.cfg)],
                         ['reminder'])
        ok = h.result('service:ntfy', True, 'stato active')
        self.assertEqual([k for k, r in h.decide_alerts([ok], state, 6 * 3600 + 120, self.day, self.cfg)],
                         ['recovered'])
        self.assertEqual(h.decide_alerts([ok], state, 6 * 3600 + 240, self.day, self.cfg), [])

    def test_confirmation_count(self):
        state = {}
        for t in (0, 120):
            self.assertEqual(h.decide_alerts([self.fail('peer')], state, t, self.day, self.cfg), [])
        self.assertEqual(len(h.decide_alerts([self.fail('peer')], state, 240, self.day, self.cfg)), 1)
        state = {}
        h.decide_alerts([self.fail('peer')], state, 0, self.day, self.cfg)
        ok = h.result('peer', True, 'ok')
        self.assertEqual(h.decide_alerts([ok], state, 120, self.day, self.cfg), [])  # never alerted: no recovery

    def test_quiet_hours_hold_non_urgent_alerts_until_morning(self):
        state = {}
        self.assertEqual(h.decide_alerts([self.fail()], state, 0, self.night, self.cfg), [])
        urgent = self.fail('service:mosquitto', urgent=True)
        self.assertEqual(len(h.decide_alerts([urgent], state, 0, self.night, self.cfg)), 1)
        morning = datetime(2026, 10, 23, 7, 0)
        self.assertEqual([r['id'] for k, r in h.decide_alerts([self.fail()], state, 18000, morning, self.cfg)],
                         ['service:ntfy'])

    def test_quiet_window(self):
        self.assertTrue(h.quiet(datetime(2026, 1, 1, 23, 30), [23, 7]))
        self.assertTrue(h.quiet(datetime(2026, 1, 1, 6, 59), [23, 7]))
        self.assertFalse(h.quiet(datetime(2026, 1, 1, 7, 0), [23, 7]))


class RunTests(unittest.TestCase):
    def test_main_runs_due_checks_publishes_and_alerts_without_secrets(self):
        with tempfile.TemporaryDirectory() as folder:
            token = os.path.join(folder, 'ntfy.token')
            open(token, 'w').write('tk_very_secret\n')
            cfg = {'host_label': '.20', 'state_file': os.path.join(folder, 'state', 'state.json'),
                   'checks': ['services', 'internet'], 'services': ['ntfy'],
                   'publish': {'topic': 'zara/system/health/20/stat'}, 'ntfy': {'token_file': token}}
            path = os.path.join(folder, 'config.json')
            with open(path, 'w') as handle:
                json.dump(cfg, handle)
            sent, published = [], []

            def fake_run(cmd, timeout=15):
                return (3, 'inactive\n') if cmd[:2] == ['systemctl', 'is-active'] else (0, '')
            with patch.object(h, 'run', fake_run), patch.object(h, 'tcp_open', lambda host, port, timeout=3: True), \
                    patch.object(h, 'send_ntfy', lambda spec, *a: sent.append(a) or True), \
                    patch.object(h, 'publish_status', lambda spec, payload: published.append(payload) or True):
                summary = h.main(path)
                h.main(path)
            self.assertFalse(summary['ok'])
            self.assertEqual(len(sent), 1)  # second run: no repeated alert
            self.assertEqual(sent[0][0], '.20: Servizio ntfy')
            self.assertNotIn('tk_very_secret', json.dumps(sent) + ''.join(published))
            body = json.loads(published[0])
            self.assertEqual(body['schema'], 'domopi.system_health.v1')
            self.assertEqual({c['id'] for c in body['checks']}, {'service:ntfy', 'internet'})
            with open(cfg['state_file']) as handle:
                state = json.load(handle)
            self.assertIn('internet', state['_last_run'])

    def test_broken_check_is_reported_not_fatal(self):
        with tempfile.TemporaryDirectory() as folder:
            cfg = {'host_label': '.15', 'state_file': os.path.join(folder, 'state.json'), 'checks': ['emontx_sign']}
            path = os.path.join(folder, 'config.json')
            with open(path, 'w') as handle:
                json.dump(cfg, handle)
            summary = h.main(path)  # no emontx_sign section: KeyError inside the check
            self.assertEqual(summary['checks'][0]['id'], 'check:emontx_sign')
            self.assertFalse(summary['ok'])


if __name__ == '__main__':
    unittest.main()
