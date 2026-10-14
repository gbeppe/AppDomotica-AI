"""Set the MQTT login of a Node-RED mqtt-broker config node (run on .20).

Usage: python3 set_broker_user.py BROKER_NODE_ID USER PASSWORD_FILE

The password is read from PASSWORD_FILE and sent only to the Node-RED Admin API (it is stored
encrypted in flows_domopi_cred.json); it is never printed or written to the repository. A full
backup of the project files and of the runtime flows is taken first. Rollback: run again with
the previous user and a file holding the previous password.
"""
import datetime, json, os, pathlib, shutil, sys, urllib.request

os.umask(0o077)
node_id, user, password_file = sys.argv[1:4]
password = pathlib.Path(password_file).read_text().strip()
assert password and user
cfg = json.loads((pathlib.Path.home() / '.node-red/.cli-config.json').read_text())
url = 'https://domopi.tailf30ba8.ts.net:1880'
headers = {'Authorization': 'Bearer ' + cfg['tokens']['access_token'], 'Node-RED-API-Version': 'v2'}


def api(body=None):
    h = dict(headers)
    if body is not None:
        h.update({'Content-Type': 'application/json', 'Node-RED-Deployment-Type': 'nodes'})
    req = urllib.request.Request(url + '/flows', data=None if body is None else json.dumps(body).encode(), headers=h)
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


live = api()
broker = next(n for n in live['flows'] if n['id'] == node_id)
assert broker['type'] == 'mqtt-broker', broker['type']
backup = pathlib.Path.home() / 'AI_backups/nodered' / (datetime.datetime.now(datetime.timezone.utc).strftime(
    '%Y%m%dT%H%M%SZ') + '-before-broker-user')
backup.mkdir(parents=True, mode=0o700)
(backup / 'runtime-before.json').write_text(json.dumps(live, indent=2))
project = pathlib.Path.home() / '.node-red/projects/domopi'
for name in ('flows_domopi.json', 'flows_domopi_cred.json'):
    if (project / name).exists():
        shutil.copy2(project / name, backup / name)

planned = json.loads(json.dumps(live))
for n in planned['flows']:
    if n['id'] == node_id:
        n['credentials'] = {'user': user, 'password': password}
result = api(planned)
after = api()
stripped = lambda flows: [{k: v for k, v in n.items() if k != 'credentials'} for n in flows]
assert stripped(after['flows']) == stripped(live['flows']), 'flows changed beyond the broker credentials'
print(json.dumps({'result': 'deployed', 'broker': broker.get('name'), 'user': user, 'revision': result['rev'],
                  'backup': str(backup)}))
