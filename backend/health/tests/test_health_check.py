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
    ctx = {'run': run, 'read_file': lambda path: '', 'tcp_open': lambda host, port: True, 'ping': lambda host: True,
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
        usb = h.check_kernel_errors({}, context({'journalctl -k': (0, 'sd 0:0:0:0: [sda] tag#3 uas_eh_abort_handler 0 uas-tag 4\n')}))
        self.assertFalse(usb[0]['ok'])

    def test_temperature_warning_then_urgent(self):
        def temp(c):
            ctx = context({'vcgencmd measure_temp': (0, "temp=%s'C\n" % c)},
                          disk_usage=lambda p: Usage(0, 0, 9000 * 1024 * 1024), mem_available_mb=lambda: 900)
            return [r for r in h.check_resources({}, ctx) if r['id'] == 'temperature'][0]
        self.assertTrue(temp('52.5')['ok'])
        warm = temp('72.0')
        self.assertFalse(warm['ok'])
        self.assertFalse(warm['urgent'])
        self.assertTrue(temp('76.0')['urgent'])

    def test_ssd_smart(self):
        def smart(passed='PASSED', realloc=0, crc=0, temp=26, life=None):
            rows = ['=== START OF READ SMART DATA SECTION ===',
                    'SMART overall-health self-assessment test result: %s' % passed,
                    'ID# ATTRIBUTE_NAME          FLAG     VALUE WORST THRESH TYPE      UPDATED  WHEN_FAILED RAW_VALUE',
                    '  5 Reallocated_Sector_Ct   0x0032   100   100   050    Old_age   Always       -       %d' % realloc,
                    '194 Temperature_Celsius     0x0022   100   100   050    Old_age   Always       -       %d (Min/Max 22/33)' % temp,
                    '199 UDMA_CRC_Error_Count    0x0032   100   100   050    Old_age   Always       -       %d' % crc]
            if life is not None:
                rows.append('231 SSD_Life_Left           0x0013   %03d   %03d   010    Pre-fail  Always       -       0' % (life, life))
            return (0, '\n'.join(rows) + '\n')
        state = {}
        ok = {r['id']: r for r in h.check_ssd({}, context({'smartctl': smart()}, state=state))}
        self.assertTrue(all(r['ok'] for r in ok.values()))
        self.assertEqual(state['_ssd'], {'5': 0, '199': 0})
        crc = {r['id']: r for r in h.check_ssd({}, context({'smartctl': smart(crc=3)}, state=state))}
        self.assertFalse(crc['ssd_errors']['ok'])
        self.assertFalse(crc['ssd_errors']['urgent'])
        self.assertIn('cavo', crc['ssd_errors']['advice'])
        bad = {r['id']: r for r in h.check_ssd({}, context({'smartctl': smart(passed='FAILED!', realloc=8, temp=70, life=15)}, state=state))}
        self.assertFalse(bad['ssd']['ok'])
        self.assertTrue(bad['ssd']['urgent'])
        self.assertTrue(bad['ssd_errors']['urgent'])
        self.assertFalse(bad['ssd_life']['ok'])
        self.assertFalse(bad['ssd_temperature']['ok'])
        missing = h.check_ssd({}, context({'smartctl': (2, 'Smartctl open device: /dev/sda failed\n')}))
        self.assertEqual([r['ok'] for r in missing], [False])

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


class BackupTests(unittest.TestCase):
    NOW = 1_793_000_000.0   # 2026-10-26T08:53:20Z
    CFG = {'backups': {'rasp': [{'mode': 'snapshot', 'max_age_h': 26}, {'mode': 'image', 'max_age_h': 192}],
                       'emoncms': {'status_file': '/media/nas-backup/emoncms/status.json',
                                   'mount': '/media/nas-backup', 'max_age_h': 26}}}

    def status(self, ok=True, hours=2, dry=False, details='done'):
        stamp = datetime.fromtimestamp(self.NOW - hours * 3600).astimezone().isoformat(timespec='seconds')
        return json.dumps({'schema': 'domopi.backup_status.v1', 'ok': ok, 'last_ok': stamp,
                           'details': details, 'dry_run': dry})

    def run_check(self, files, emoncms=(0, None), mounted=0):
        def read_file(path):
            if path not in files:
                raise OSError(path)
            return files[path]
        em_code, em_text = emoncms
        if em_text is None:
            em_text = json.dumps({'last_run': '2026-10-26T03:47:56Z', 'mode': 'verify', 'errors': False})
        ctx = context({'timeout 20 cat': (em_code, em_text), 'findmnt': (mounted, '')},
                      read_file=read_file, now=lambda: self.NOW)
        return {r['id']: r for r in h.check_backups(self.CFG, ctx)}

    def files(self, snap, img):
        return {'/var/lib/domopi-backup/status-snapshot.json': snap, '/var/lib/domopi-backup/status-image.json': img}

    def test_recent_backups_are_ok(self):
        out = self.run_check(self.files(self.status(hours=5), self.status(hours=24 * 6)))
        self.assertTrue(all(r['ok'] for r in out.values()), out)
        self.assertIn('5 ore fa', out['backup:snapshot']['detail'])
        self.assertIn('6 giorni fa', out['backup:image']['detail'])
        self.assertIn('verify', out['backup:emoncms']['detail'])

    def test_old_or_failed_or_missing(self):
        out = self.run_check(self.files(self.status(hours=27), self.status(hours=24 * 9)))
        self.assertFalse(out['backup:snapshot']['ok'])
        self.assertFalse(out['backup:image']['ok'])
        failed = self.status(ok=False, hours=3, details='rsync partition 2 exit 137')
        out = self.run_check(self.files(failed, self.status()))
        self.assertFalse(out['backup:snapshot']['ok'])
        self.assertIn('fallito (rsync partition 2 exit 137)', out['backup:snapshot']['detail'])
        out = self.run_check({})
        self.assertFalse(out['backup:image']['ok'])
        self.assertIn('nessun esito', out['backup:image']['detail'])

    def test_dry_run_is_not_a_failure_but_does_not_refresh(self):
        out = self.run_check(self.files(self.status(ok=True, hours=30, dry=True), self.status()))
        self.assertFalse(out['backup:snapshot']['ok'])
        self.assertNotIn('fallito', out['backup:snapshot']['detail'])

    def test_emoncms_old_errors_or_unmounted(self):
        old = json.dumps({'last_run': '2026-10-24T03:00:00Z', 'mode': 'sync', 'errors': False})
        self.assertFalse(self.run_check(self.files(self.status(), self.status()), (0, old))['backup:emoncms']['ok'])
        bad = json.dumps({'last_run': '2026-10-26T03:47:56Z', 'mode': 'sync', 'errors': True})
        res = self.run_check(self.files(self.status(), self.status()), (0, bad))['backup:emoncms']
        self.assertFalse(res['ok'])
        self.assertIn('con errori', res['detail'])
        res = self.run_check(self.files(self.status(), self.status()), (124, ''), mounted=1)['backup:emoncms']
        self.assertFalse(res['ok'])
        self.assertIn('non montata', res['detail'])

    def test_iso_epoch(self):
        self.assertEqual(h.iso_epoch('2026-10-25T03:47:56Z'), h.iso_epoch('2026-10-25T04:47:56+01:00'))
        self.assertIsNone(h.iso_epoch('ieri'))
        self.assertIsNone(h.iso_epoch(None))


class PowerwallChargeTests(unittest.TestCase):
    CFG = {'powerwall_charge': {'soc_feed': 304, 'battery_feed': 306, 'grid_feed': 305, 'target_feed': 461,
                                'discharge_w': 200, 'export_w': 500, 'soc_max': 95, 'readings': 10, 'clear_readings': 15}}

    def step(self, state, soc, battery, grid, target=300.0, now=1_000_000.0):
        values = {304: soc, 306: battery, 305: grid, 461: target}

        def run(cmd, timeout=15):
            feed = int(cmd[2].split(':')[1])
            return 0, str(now - 30 if cmd[3] == 'time' else values[feed]) + '\n'
        return h.check_powerwall_charge(self.CFG, context(run=run, now=lambda: now, state=state))[0]

    def test_23_october_pattern_alerts_after_20_minutes(self):
        # 23/10 12:00: battery +453 W to the house, grid -4017 W, SOC 38 %
        state = {}
        for i in range(9):
            self.assertTrue(self.step(state, 38, 453, -4017)['ok'])
        res = self.step(state, 38, 453, -4017)
        self.assertFalse(res['ok'])
        self.assertIn('si scarica mentre il solare va in rete', res['detail'])
        self.assertIn('il gateway chiede 300 W', res['detail'])

    def test_charge_pause_and_normal_days_never_alert(self):
        state = {}
        for _ in range(30):
            self.assertTrue(self.step(state, 80, 0, -2500)['ok'])        # pause at 80 %, export
            self.assertTrue(self.step(state, 50, -2000, -100)['ok'])     # charging from solar
            self.assertTrue(self.step(state, 30, 400, 50)['ok'])         # evening: battery feeds the house
            self.assertTrue(self.step(state, 99, 300, -800)['ok'])       # full battery

    def test_clears_only_after_30_quiet_minutes(self):
        state = {}
        for _ in range(10):
            self.step(state, 38, 453, -4017)
        for _ in range(14):
            self.assertFalse(self.step(state, 38, 0, -3000)['ok'])      # passing cloud: still raised
        self.assertTrue(self.step(state, 38, 0, -3000)['ok'])

    def test_stale_data_is_ignored(self):
        state = {'_pw_charge': {'count': 5, 'active': False, 'clear': 0}}
        def run(cmd, timeout=15):
            return 0, ('1000.0' if cmd[3] == 'time' else '450') + '\n'      # data 999 000 s old
        h.check_powerwall_charge(self.CFG, context(run=run, now=lambda: 1_000_000.0, state=state))
        self.assertEqual(state['_pw_charge']['count'], 5)


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
