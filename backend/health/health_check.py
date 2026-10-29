#!/usr/bin/env python3
"""DomoPi health check (phase S1, backups since S4/B4): one run, started every 2 minutes by a systemd timer.

Runs on both Raspberry Pis, outside Node-RED and House AI, which are among the things it checks.
Reads only; never touches OpenSprinkler; never prints or publishes secrets (credentials come from
files named in the configuration). Each run:
  1. runs the checks that are due (light ones every run, heavy ones every 15 minutes);
  2. keeps a small state file and turns transitions into ntfy alerts (one per fault, reminder every
     6 hours, a message when it is fixed; non-urgent ones wait outside the quiet hours);
  3. publishes the summary retained on zara/system/health/<host>/stat (no secrets, no hostnames
     beyond the house addresses).
Python 3.7 compatible (the .15 runs Raspbian 10), standard library plus paho when configured.
"""
import calendar
import glob
import json
import os
import re
import shutil
import socket
import ssl
import subprocess
import sys
import time
import urllib.request
from datetime import datetime

REMINDER_S = 6 * 3600


# ---------------------------------------------------------------------------------------------
# Small helpers (replaceable in tests)
# ---------------------------------------------------------------------------------------------

def run(cmd, timeout=15):
    """(exit code, stdout) of a command; (127, '') when it does not exist."""
    try:
        done = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                              timeout=timeout, universal_newlines=True)
        return done.returncode, done.stdout
    except (OSError, subprocess.TimeoutExpired):
        return 127, ''


def tcp_open(host, port, timeout=3):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def ping(host):
    return run(['ping', '-c', '1', '-W', '2', host], timeout=5)[0] == 0


def http_alive(url, insecure=False, timeout=5):
    """True when the server answers HTTP at all (401 included: alive, just protected)."""
    context = ssl._create_unverified_context() if insecure else None
    try:
        urllib.request.urlopen(url, timeout=timeout, context=context)
        return True
    except urllib.error.HTTPError:
        return True
    except (OSError, ValueError):
        return False


def read_secret(path):
    with open(path) as handle:
        return handle.read().strip()


def result(check_id, ok, detail, advice='', urgent=False, title=None):
    return {'id': check_id, 'ok': bool(ok), 'detail': detail, 'advice': advice,
            'urgent': urgent, 'title': title or check_id}


# ---------------------------------------------------------------------------------------------
# Checks: each returns a list of results
# ---------------------------------------------------------------------------------------------

def check_power(cfg, ctx):
    code, out = ctx['run'](['vcgencmd', 'get_throttled'])
    match = re.search(r'throttled=(0x[0-9a-fA-F]+)', out)
    flags = int(match.group(1), 16) if code == 0 and match else 0
    now_low = bool(flags & 0x1)
    code, kernel = ctx['run'](['journalctl', '-k', '--since', '-1 hour', '--no-pager', '-q'], timeout=30)
    events = len(re.findall(r'(?i)under-?voltage', kernel)) if code == 0 else 0
    ok = not now_low and events <= cfg.get('undervoltage_per_hour', 3)
    return [result('power', ok,
                   'sottotensione in corso' if now_low else
                   '%d eventi di sottotensione nell\'ultima ora' % events,
                   'Controlla l\'alimentatore e il cavo; scollega gli USB non essenziali.',
                   urgent=now_low, title='Alimentazione')]


def check_services(cfg, ctx):
    out = []
    for name in cfg.get('services', []):
        code, state = ctx['run'](['systemctl', 'is-active', name])
        state = state.strip() or 'sconosciuto'
        out.append(result('service:' + name, state == 'active', 'stato %s' % state,
                          'Servizio fermo: «sudo systemctl restart %s», poi controlla il log.' % name,
                          urgent=name in cfg.get('urgent_services', []), title='Servizio ' + name))
    return out


def mosquitto_settings(paths):
    """{'persistence': value, 'anonymous:<listener>': value}; 'default' before any listener."""
    settings, listener = {}, 'default'
    for path in paths:
        try:
            with open(path) as handle:
                lines = handle.read().splitlines()
        except OSError:
            continue
        for line in lines:
            words = line.split()
            if not words or words[0].startswith('#'):
                continue
            if words[0] == 'listener' and len(words) > 1:
                listener = words[1]
            elif words[0] == 'persistence' and len(words) > 1:
                settings['persistence'] = words[1]
            elif words[0] == 'allow_anonymous' and len(words) > 1:
                settings['anonymous:' + listener] = words[1]
    return settings


def check_mosquitto_config(cfg, ctx):
    spec = cfg['mosquitto']
    paths = []
    for pattern in spec['files']:
        paths += sorted(ctx['glob'](pattern))
    actual = ctx['mosquitto_settings'](paths)
    out = []
    for key, expected in sorted(spec['expect'].items()):
        value = actual.get(key, 'non impostato')
        out.append(result('mosquitto:' + key, value == expected,
                          '%s = %s (atteso %s)' % (key, value, expected),
                          'La configurazione di Mosquitto non è quella attesa: verifica se il cambio è '
                          'voluto (nessun ripristino automatico).', title='Configurazione Mosquitto'))
    return out


def check_broker(cfg, ctx):
    spec = cfg['broker']
    ok = ctx['broker_probe'](spec)
    return [result('broker', ok,
                   'collegamento e messaggio retained ricevuti' if ok else
                   'collegamento non riuscito o retained «%s» assente' % spec.get('retained_topic', ''),
                   'Controlla Mosquitto: «systemctl status mosquitto»; se i retained mancano, verifica '
                   '«persistence».', urgent=True, title='Broker MQTT')]


def check_http(cfg, ctx):
    out = []
    for item in cfg.get('http', []):
        ok = ctx['http_alive'](item['url'], item.get('insecure', False))
        out.append(result('http:' + item['name'], ok, 'risponde' if ok else 'non risponde',
                          'Controlla il servizio e il suo log.', title=item['title']))
    return out


def check_tailscale(cfg, ctx):
    code, out = ctx['run'](['tailscale', 'status', '--json'], timeout=15)
    state = 'sconosciuto'
    if code == 0:
        try:
            state = json.loads(out).get('BackendState', 'sconosciuto')
        except ValueError:
            pass
    return [result('tailscale', state == 'Running', 'stato %s' % state,
                   'Se è appena ripartito il router: «sudo systemctl restart tailscaled»; se non basta, '
                   'riavvia il router e ricontrollo.', title='Tailscale')]


def check_reachability(cfg, ctx):
    out = []
    peer = cfg.get('peer')
    if peer:
        ok = ctx['ping'](peer['ip']) and ctx['tcp_open'](peer['ip'], peer.get('port', 1883))
        out.append(result('peer', ok, '%s %s' % (peer['name'], 'raggiungibile' if ok else 'non risponde'),
                          'Controlla alimentazione e cavo di rete di %s; se serve, togli e ridai corrente.'
                          % peer['name'], urgent=True, title='Raspberry %s' % peer['name']))
    if cfg.get('router'):
        ok = ctx['ping'](cfg['router'])
        out.append(result('router', ok, 'router %s' % ('raggiungibile' if ok else 'non risponde'),
                          'Controlla il router ASUS.', urgent=True, title='Router'))
    return out


def check_internet(cfg, ctx):
    ok = any(ctx['tcp_open'](host, 443) for host in cfg.get('internet', ['1.1.1.1', '8.8.8.8']))
    return [result('internet', ok, 'Internet %s' % ('raggiungibile' if ok else 'non raggiungibile'),
                   'Controlla il router e la linea.', title='Internet')]


def check_resources(cfg, ctx):
    out = []
    usage = ctx['disk_usage']('/')
    free_mb = usage.free // (1024 * 1024)
    out.append(result('disk', free_mb >= cfg.get('disk_min_mb', 500), '%d MB liberi su /' % free_mb,
                      'Disco quasi pieno: «sudo du -xh / --max-depth=2 | sort -h | tail» per le cartelle '
                      'più grandi.', title='Disco'))
    available = ctx['mem_available_mb']()
    out.append(result('memory', available >= cfg.get('memory_min_mb', 100),
                      '%d MB di memoria disponibile' % available,
                      'Memoria scarsa: controlla i processi con «top».', title='Memoria'))
    code, temp = ctx['run'](['vcgencmd', 'measure_temp'])
    match = re.search(r'temp=([\d.]+)', temp)
    if code == 0 and match:
        # warning from temp_warn_c, urgent from temp_max_c (the Pi slows itself down from 80 °C; the technical
        # room reaches ~34 °C in summer)
        celsius = float(match.group(1))
        urgent = celsius > cfg.get('temp_max_c', 75)
        out.append(result('temperature', celsius <= cfg.get('temp_warn_c', 70), '%.1f °C' % celsius,
                          'CPU calda: controlla ventilazione, posizione e i cuscinetti termici del case.',
                          urgent=urgent, title='Temperatura CPU'))
    return out


KERNEL_DISK_ERRORS = re.compile(r'(?i)(mmc\d.*(error|timeout)|I/O error|EXT4-fs error|uas_eh_\w+|'
                                r'reset (SuperSpeed|high-speed) USB device|sd \d+:\d+:\d+:\d+: .*(timing out|abort))')


def check_kernel_errors(cfg, ctx):
    # The id stays sd_errors so that the alert state survives the move from SD to SSD (29/10/2026)
    code, kernel = ctx['run'](['journalctl', '-k', '--since', '-1 hour', '--no-pager', '-q'], timeout=30)
    errors = len(KERNEL_DISK_ERRORS.findall(kernel)) if code == 0 else 0
    return [result('sd_errors', errors == 0, '%d errori del disco o della USB nell\'ultima ora' % errors,
                   'Controlla cavo e adattatore dell\'SSD (porta blu); se continua, fai subito un backup.',
                   urgent=errors > 0, title='Errori del disco')]


SMART_COUNTERS = {5: 'settori riallocati', 197: 'settori in attesa', 198: 'settori illeggibili',
                  199: 'errori CRC del cavo'}
SMART_LIFE_LEFT = (169, 177, 231, 233)  # remaining life in percent, where the drive reports it


def smart_attributes(text):
    attrs = {}
    for line in text.splitlines():
        parts = line.split()
        if len(parts) >= 10 and parts[0].isdigit():
            raw = re.match(r'\d+', parts[9])
            attrs[int(parts[0])] = {'name': parts[1], 'value': int(parts[3]) if parts[3].isdigit() else None,
                                    'raw': int(raw.group(0)) if raw else None}
    return attrs


def check_ssd(cfg, ctx):
    """SMART of the system SSD behind the USB adapter: health, growing error counters, life, temperature."""
    spec = cfg.get('ssd', {})
    device = spec.get('device', '/dev/sda')
    code, text = ctx['run'](['smartctl', '-d', 'sat', '-H', '-A', device], timeout=30)
    if not re.search(r'overall-health self-assessment test result', text):
        return [result('ssd', False, 'SMART non leggibile su %s (codice %d)' % (device, code),
                       'Controlla che l\'SSD sia collegato e «sudo smartctl -d sat -a %s».' % device, title='SSD')]
    out = []
    passed = re.search(r'test result: PASSED', text) is not None
    out.append(result('ssd', passed, 'stato SMART %s' % ('PASSED' if passed else 'NON PASSED'),
                      'Il disco segnala un guasto: fai subito un backup e prepara la sostituzione.',
                      urgent=not passed, title='SSD'))
    attrs = smart_attributes(text)
    baseline = ctx['state'].setdefault('_ssd', {})
    grown = []
    for number, label in sorted(SMART_COUNTERS.items()):
        raw = attrs.get(number, {}).get('raw')
        if raw is None:
            continue
        first = baseline.setdefault(str(number), raw)
        if raw > first:
            grown.append('%s %d (erano %d)' % (label, raw, first))
    crc_only = grown and all('CRC' in item for item in grown)
    out.append(result('ssd_errors', not grown, ', '.join(grown) if grown else 'nessun contatore di errori in crescita',
                      'Errori CRC: cavo o adattatore USB, prova a ricollegarlo o a cambiarlo.' if crc_only else
                      'Il disco sta perdendo settori: fai un backup e prepara la sostituzione.',
                      urgent=bool(grown) and not crc_only, title='Errori SSD'))
    life = [attrs[n]['value'] for n in SMART_LIFE_LEFT if attrs.get(n, {}).get('value') is not None]
    if life:
        left = min(life)
        out.append(result('ssd_life', left >= spec.get('life_min_pct', 20), 'vita residua %d %%' % left,
                          'SSD consumato: prepara la sostituzione.', title='Usura SSD'))
    temp = attrs.get(194, {}).get('raw')
    if temp is not None:
        out.append(result('ssd_temperature', temp <= spec.get('temp_max_c', 65), '%d °C' % temp,
                          'SSD caldo: controlla la ventilazione intorno al disco.', title='Temperatura SSD'))
    return out


def check_clock(cfg, ctx):
    code, out = ctx['run'](['timedatectl', 'show', '-p', 'NTPSynchronized'])
    ok = code == 0 and out.strip().endswith('yes')
    return [result('clock', ok, 'orologio %s' % ('sincronizzato' if ok else 'non sincronizzato'),
                   'Le regole a orario dipendono dall\'ora: controlla «timedatectl» e la rete.',
                   title='Orologio')]


def redis_feed(ctx, feed, field):
    code, out = ctx['run'](['redis-cli', 'hget', 'feed:%d' % feed, field])
    try:
        return float(out.strip()) if code == 0 else None
    except ValueError:
        return None


def check_energy_data(cfg, ctx):
    """EmonCMS feeds still written: their last time in redis is recent."""
    out = []
    now = ctx['now']()
    for item in cfg.get('emoncms_feeds', []):
        stamp = redis_feed(ctx, item['feed'], 'time')
        age = None if stamp is None else now - stamp
        ok = age is not None and age <= item.get('max_age_s', 600)
        out.append(result('energy:' + item['name'], ok,
                          'ultimo dato %s' % ('assente' if age is None else '%d s fa' % age),
                          'EmonCMS non riceve dati da %s: controlla il dispositivo, emonhub e la rete.'
                          % item['name'], title='Dati %s' % item['name']))
    return out


def check_emontx_sign(cfg, ctx):
    """The emonTx CT1 and the Tesla grid meter must agree in sign (adapter turned: 19-23/10/2026)."""
    spec = cfg['emontx_sign']
    site = redis_feed(ctx, spec['site_feed'], 'value')
    imp = redis_feed(ctx, spec['import_feed'], 'value')
    exp = redis_feed(ctx, spec['export_feed'], 'value')
    state = ctx['state'].setdefault('_emontx_sign', {'mismatches': 0})
    if None not in (site, imp, exp) and abs(site) > spec['min_w'] and abs(imp - exp) > spec['min_w']:
        state['mismatches'] = state['mismatches'] + 1 if (site > 0) != (imp - exp > 0) else 0
    ok = state['mismatches'] < spec['mismatches']
    return [result('emontx_sign', ok,
                   'pinza CT1 e rete Powerwall %s' % ('concordi' if ok else
                   'di segno opposto da %d letture' % state['mismatches']),
                   'L\'adattatore di tensione dell\'emonTx è girato o cambiato: girane la spina di 180°.',
                   title='emonTx al contrario')]


def check_powerwall_charge(cfg, ctx):
    """23/10/2026: for hours the Powerwall powered the house while all the solar went to the grid, down to the
    reserve. In self-consumption the battery never discharges while power is exported: alert when it does for
    `readings` checks in a row (2 minutes apart). A charge pause (e.g. at 80 %) keeps the battery at ~0 W and
    does not count. Once raised, the alert clears only after `clear_readings` normal checks in a row, so a
    passing cloud does not send "fixed" and a new alert (23/10 replay: 11:00-11:20)."""
    spec = cfg['powerwall_charge']
    now = ctx['now']()
    state = ctx['state'].setdefault('_pw_charge', {'count': 0, 'active': False, 'clear': 0})
    state.setdefault('active', False)
    state.setdefault('clear', 0)
    values = {}
    for name in ('soc', 'battery', 'grid', 'target'):
        feed = spec.get(name + '_feed')
        stamp = redis_feed(ctx, feed, 'time') if feed else None
        fresh = stamp is not None and now - stamp <= spec.get('max_age_s', 600)
        values[name] = redis_feed(ctx, feed, 'value') if fresh else None
    soc, battery, grid = values['soc'], values['battery'], values['grid']
    if None not in (soc, battery, grid):
        anomalous = battery > spec['discharge_w'] and -grid > spec['export_w'] and soc < spec['soc_max']
        state['count'] = state['count'] + 1 if anomalous else 0
        state['clear'] = 0 if anomalous else state['clear'] + 1
        if state['count'] >= spec['readings']:
            state['active'] = True
        elif state['active'] and state['clear'] >= spec.get('clear_readings', 15):
            state['active'] = False
    ok = not state['active']
    target = values['target']
    detail = ('batteria %s W, rete %s W, carica %s %%%s' % (
        '?' if battery is None else int(battery), '?' if grid is None else int(grid),
        '?' if soc is None else int(soc), '' if target is None else ', il gateway chiede %d W' % target))
    if not ok:
        detail = 'si scarica mentre il solare va in rete (%s)' % detail
    return [result('powerwall_charge', ok, detail,
                   'Controlla l\'app Tesla (modalità, riserva, avvisi). Il 24/10 si è sbloccata spostando la riserva '
                   '(0 -> 10 -> 0 %). Il dettaglio è nei feed pw_* di EmonCMS.', title='Powerwall non carica')]


def iso_epoch(text):
    """Seconds since the epoch of '2026-10-25T14:18:12+01:00' or '2026-10-25T03:47:56Z'; None if unreadable."""
    try:
        if text.endswith('Z'):
            return calendar.timegm(time.strptime(text, '%Y-%m-%dT%H:%M:%SZ'))
        return datetime.fromisoformat(text).timestamp()
    except (AttributeError, TypeError, ValueError):
        return None


def age_text(seconds):
    return '%.0f ore' % (seconds / 3600) if seconds < 3 * 86400 else '%.0f giorni' % (seconds / 86400)


def check_backups(cfg, ctx):
    """Phase S4 (B4): the backups on the QNAP are recent and the last run did not fail."""
    spec = cfg['backups']
    now = ctx['now']()
    out = []
    for item in spec.get('rasp', []):
        title = {'snapshot': 'Backup mirato', 'image': 'Immagine di backup'}[item['mode']]
        path = os.path.join(spec.get('status_dir', '/var/lib/domopi-backup'), 'status-%s.json' % item['mode'])
        try:
            status = json.loads(ctx['read_file'](path))
        except (OSError, ValueError):
            status = None
        last_ok = iso_epoch(status.get('last_ok') or '') if status else None
        age = None if last_ok is None else now - last_ok
        failed = status is not None and not status.get('ok') and not status.get('dry_run')
        ok = age is not None and age <= item['max_age_h'] * 3600 and not failed
        if status is None:
            detail = 'nessun esito in %s' % path
        elif failed:
            detail = 'ultimo giro fallito (%s); ultimo riuscito %s' % (
                status.get('details', '?'), 'mai' if age is None else age_text(age) + ' fa')
        else:
            detail = 'ultimo riuscito %s' % ('mai' if age is None else age_text(age) + ' fa')
        out.append(result('backup:' + item['mode'], ok, detail,
                          'Guarda «journalctl -u domopi-backup-%s -n 30» e lo stato del QNAP 192.168.1.4; '
                          'il giro si rilancia con «sudo systemctl start domopi-backup-%s».'
                          % (item['mode'], item['mode']), title=title))
    em = spec.get('emoncms')
    if em:
        # reading the file mounts the share (systemd automount); a timeout keeps a dead QNAP from blocking
        code, text = ctx['run'](['timeout', '20', 'cat', em['status_file']], timeout=25)
        mounted = ctx['run'](['findmnt', '-n', '-t', 'nfs,nfs4', em['mount']])[0] == 0
        try:
            status = json.loads(text) if code == 0 else None
        except ValueError:
            status = None
        last = iso_epoch(status.get('last_run') or '') if status else None
        age = None if last is None else now - last
        errors = bool(status.get('errors')) if status else False
        ok = mounted and age is not None and age <= em['max_age_h'] * 3600 and not errors
        if not mounted or status is None:
            detail = 'cartella %s non montata o esito illeggibile' % em['mount']
        else:
            detail = 'ultimo giro (%s) %s fa%s' % (status.get('mode', '?'), age_text(age) if age is not None else '?',
                                                  ', con errori' if errors else '')
        out.append(result('backup:emoncms', ok, detail,
                          'Il backup di emoncms (emoncms-drive-backup) non è aggiornato: controlla il QNAP, '
                          '«findmnt %s» e /var/log/emoncms/drivebackup.log.' % em['mount'],
                          title='Backup emoncms'))
    return out


CHECKS = {
    'power': (check_power, 0), 'services': (check_services, 0), 'broker': (check_broker, 0),
    'http': (check_http, 0), 'tailscale': (check_tailscale, 0), 'reachability': (check_reachability, 0),
    'energy_data': (check_energy_data, 0), 'emontx_sign': (check_emontx_sign, 0),
    'mosquitto_config': (check_mosquitto_config, 900), 'internet': (check_internet, 900),
    'resources': (check_resources, 900), 'kernel_errors': (check_kernel_errors, 900),
    'clock': (check_clock, 900), 'backups': (check_backups, 900), 'powerwall_charge': (check_powerwall_charge, 0),
    'ssd': (check_ssd, 3600),
}


# ---------------------------------------------------------------------------------------------
# Probes that need credentials
# ---------------------------------------------------------------------------------------------

def broker_probe(spec):
    """Connect and, when configured, receive one retained message. Password only from its file."""
    if spec.get('client') == 'paho':
        import paho.mqtt.client as mqtt
        got = {'connected': False, 'retained': not spec.get('retained_topic')}
        client = mqtt.Client()
        if spec.get('user'):
            client.username_pw_set(spec['user'], read_secret(spec['password_file']))
        client.on_connect = lambda c, u, f, rc: (got.update(connected=rc == 0),
                                                 spec.get('retained_topic') and c.subscribe(spec['retained_topic']))
        client.on_message = lambda c, u, m: got.update(retained=got['retained'] or m.retain)
        try:
            client.connect(spec.get('host', '127.0.0.1'), spec.get('port', 1883), 10)
            deadline = time.time() + 5
            while time.time() < deadline and not (got['connected'] and got['retained']):
                client.loop(timeout=0.5)
            client.disconnect()
        except OSError:
            return False
        return got['connected'] and got['retained']
    cmd = ['mosquitto_sub', '-h', spec.get('host', '127.0.0.1'), '-p', str(spec.get('port', 1883)),
           '-C', '1', '-W', '5', '-t', spec['retained_topic']]
    env = dict(os.environ, XDG_CONFIG_HOME=spec['xdg_config_home']) if spec.get('xdg_config_home') else None
    try:
        done = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=10,
                              universal_newlines=True, env=env)
        return done.returncode == 0 and done.stdout.strip() != ''
    except (OSError, subprocess.TimeoutExpired):
        return False


def publish_status(spec, payload):
    """Retained summary for the app (S3). Failures are not fatal: the alerts still go out."""
    try:
        if spec.get('client') == 'paho':
            import paho.mqtt.client as mqtt
            client = mqtt.Client()
            client.username_pw_set(spec['user'], read_secret(spec['password_file']))
            client.connect(spec['host'], spec.get('port', 1883), 10)
            client.loop_start()
            client.publish(spec['topic'], payload, qos=1, retain=True).wait_for_publish()
            client.loop_stop()
            client.disconnect()
            return True
        env = dict(os.environ, XDG_CONFIG_HOME=spec['xdg_config_home'])
        done = subprocess.run(['mosquitto_pub', '-h', spec['host'], '-p', str(spec.get('port', 1883)),
                               '-q', '1', '-r', '-t', spec['topic'], '-s'], input=payload,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10,
                              universal_newlines=True, env=env)
        return done.returncode == 0
    except Exception:  # noqa: BLE001 - the health check must never crash on publishing
        return False


def send_ntfy(spec, title, message, priority, tags):
    """POST to ntfy with the token from its file. The .15 reaches it by IP with the TLS name."""
    data = message.encode('utf-8')
    headers = {'Title': title.encode('utf-8').decode('latin-1', 'replace'), 'Priority': str(priority),
               'Tags': tags, 'Authorization': 'Bearer ' + read_secret(spec['token_file'])}
    try:
        if spec.get('connect_ip'):
            import http.client
            host, _, port = spec['host'].partition(':')
            context = ssl.create_default_context()

            class Pinned(http.client.HTTPSConnection):
                def connect(self):
                    sock = socket.create_connection((spec['connect_ip'], int(port or 443)), timeout=10)
                    self.sock = context.wrap_socket(sock, server_hostname=host)

            conn = Pinned(host, int(port or 443), timeout=10, context=context)
            conn.request('POST', '/' + spec['topic'], body=data, headers=dict(headers, Host=spec['host']))
            ok = conn.getresponse().status == 200
            conn.close()
            return ok
        request = urllib.request.Request(spec['url'], data=data, headers=headers, method='POST')
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status == 200
    except (OSError, ValueError):
        return False


# ---------------------------------------------------------------------------------------------
# State, alerts and the run
# ---------------------------------------------------------------------------------------------

def quiet(now_dt, hours):
    start, end = hours
    return now_dt.hour >= start or now_dt.hour < end if start > end else start <= now_dt.hour < end


def decide_alerts(results, state, now, now_dt, cfg):
    """Update per-check state; return the alerts to send now [(kind, result)]."""
    alerts = []
    confirm = cfg.get('confirm', {})
    hours = cfg.get('quiet_hours', [23, 7])
    for res in results:
        entry = state.setdefault(res['id'], {'ok': True, 'fails': 0, 'since': now, 'alerted_at': None})
        if res['ok']:
            if not entry['ok'] and entry['alerted_at'] is not None:
                alerts.append(('recovered', res))
            entry.update(ok=True, fails=0, since=now, alerted_at=None)
            continue
        entry['fails'] += 1
        needed = confirm.get(res['id'].split(':')[0], 1)
        if entry['fails'] < needed:
            continue
        if entry['ok']:
            entry.update(ok=False, since=now)
        silent = quiet(now_dt, hours) and not res['urgent']
        due = entry['alerted_at'] is None or now - entry['alerted_at'] >= REMINDER_S
        if due and not silent:
            alerts.append(('fault' if entry['alerted_at'] is None else 'reminder', res))
            entry['alerted_at'] = now
    return alerts


def main(config_path):
    with open(config_path) as handle:
        cfg = json.load(handle)
    state_path = cfg['state_file']
    try:
        with open(state_path) as handle:
            state = json.load(handle)
    except (OSError, ValueError):
        state = {}
    now = time.time()
    ctx = {'run': run, 'read_file': read_secret, 'tcp_open': tcp_open, 'ping': ping, 'http_alive': http_alive,
           'broker_probe': broker_probe, 'glob': glob.glob, 'mosquitto_settings': mosquitto_settings,
           'disk_usage': shutil.disk_usage, 'mem_available_mb': mem_available_mb,
           'now': lambda: now, 'state': state}
    last_runs = state.setdefault('_last_run', {})
    previous = state.setdefault('_results', {})
    for name in cfg['checks']:
        check, every = CHECKS[name]
        if every and now - last_runs.get(name, 0) < every:
            continue
        try:
            fresh = check(cfg, ctx)
        except Exception as error:  # noqa: BLE001 - one broken check must not stop the others
            fresh = [result('check:' + name, False, 'controllo non eseguito: %s' % type(error).__name__,
                            'Errore del controllo stesso: guarda «journalctl -u domopi-health».',
                            title='Controllo %s' % name)]
        previous[name] = fresh
        last_runs[name] = now
    results = [res for name in cfg['checks'] for res in previous.get(name, [])]
    alerts = decide_alerts(results, state, now, datetime.fromtimestamp(now), cfg)
    host = cfg['host_label']
    for kind, res in alerts:
        if kind == 'recovered':
            title, body, priority, tags = '%s: %s di nuovo a posto' % (host, res['title']), res['detail'], 2, 'white_check_mark'
        else:
            title = '%s: %s%s' % (host, res['title'], ' (ancora)' if kind == 'reminder' else '')
            body = '%s.\nCosa fare: %s' % (res['detail'], res['advice'])
            priority, tags = (5 if res['urgent'] else 3), 'warning'
        sent = send_ntfy(cfg['ntfy'], title, body, priority, tags) if cfg.get('ntfy') else False
        print('%s %s %s (%s)' % (kind, res['id'], res['detail'], 'inviato' if sent else 'non inviato'))
    summary = {'schema': 'domopi.system_health.v1', 'host': host, 'checked_at_ms': int(now * 1000),
               'ok': all(res['ok'] for res in results),
               'checks': [{'id': res['id'], 'title': res['title'], 'ok': res['ok'], 'detail': res['detail'],
                           'advice': '' if res['ok'] else res['advice']} for res in results]}
    if cfg.get('publish'):
        publish_status(cfg['publish'], json.dumps(summary, ensure_ascii=False))
    os.makedirs(os.path.dirname(state_path), exist_ok=True)
    temp = state_path + '.tmp'
    with open(temp, 'w') as handle:
        json.dump(state, handle)
    os.replace(temp, state_path)
    return summary


def mem_available_mb():
    with open('/proc/meminfo') as handle:
        for line in handle:
            if line.startswith('MemAvailable:'):
                return int(line.split()[1]) // 1024
    return 0


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '/etc/domopi-health/config.json')
