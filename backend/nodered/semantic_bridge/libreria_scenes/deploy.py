"""Run on .20. Explicit prepare/apply modes; TLS verified; credentials never printed."""
import copy,datetime,hashlib,json,os,pathlib,shutil,sys,urllib.request
os.umask(0o077)
root=pathlib.Path(__file__).resolve().parent
cfg=json.loads((pathlib.Path.home()/'.node-red/.cli-config.json').read_text())
url='https://domopi.tailf30ba8.ts.net:1880'
assert cfg['target'].rstrip('/')==url
headers={'Authorization':'Bearer '+cfg['tokens']['access_token'],'Node-RED-API-Version':'v2'}
def api(body=None):
 h=dict(headers)
 if body is not None:h.update({'Content-Type':'application/json','Node-RED-Deployment-Type':'nodes'})
 req=urllib.request.Request(url+'/flows',data=None if body is None else json.dumps(body).encode(),headers=h)
 with urllib.request.urlopen(req,timeout=60) as response:return json.load(response)
def digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()
mode=sys.argv[1]
if mode=='prepare':
 live=api();by={n['id']:n for n in live['flows']}
 old=json.loads((root/'rollback.json').read_text());new=json.loads((root/'update.json').read_text())
 patched=copy.deepcopy(live);out={n['id']:n for n in patched['flows']}
 new_by={n['id']:n for n in new}
 for before in old:
  after=new_by[before['id']]
  id=before['id'];assert id in by
  for field in ['type','z','func','outputs','wires']:assert by[id].get(field)==before.get(field),'Runtime drift '+id+' '+field
  for field in ['func','outputs','wires']:
   if field in after:out[id][field]=after[field]
 old_ids={n['id'] for n in old}
 for n in new:
  if n['id'] not in old_ids:
   assert n['id'] not in out,'New ID collision'
   patched['flows'].append(n)
 assert len(old)==15 and len(new)==24
 backup=pathlib.Path.home()/'AI_backups/nodered'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ-before-libreria-scenes')
 backup.mkdir(parents=True,mode=0o700)
 (backup/'runtime-before.json').write_text(json.dumps(live,indent=2))
 (backup/'runtime-planned.json').write_text(json.dumps(patched,indent=2))
 project=pathlib.Path.home()/'.node-red/projects/domopi'
 for name in ['flows_domopi.json','flows_domopi_cred.json']:
  src=project/name
  if src.exists():shutil.copy2(src,backup/name)
 (backup/'plan.json').write_text(json.dumps({'modified_ids':[n['id'] for n in old],'before_hash':digest(live),'planned_hash':digest(patched)},indent=2))
 print(backup);print('Runtime verified; 15 changes and 9 additions planned; full backup saved.')
elif mode=='apply':
 backup=pathlib.Path(sys.argv[2]);before=json.loads((backup/'runtime-before.json').read_text());planned=json.loads((backup/'runtime-planned.json').read_text())
 current=api();assert current==before,'Runtime changed after preparation; stop'
 result=api(planned)
 actual=api();assert digest(actual['flows'])==digest(planned['flows']),'Post-deploy mismatch; inspect runtime before rollback'
 (backup/'runtime-after.json').write_text(json.dumps(actual,indent=2))
 print(json.dumps({'result':'verified','revision':result['rev'],'backup':str(backup),'modified_nodes':15,'added_nodes':9}))
else:raise SystemExit('Use prepare or apply BACKUP_DIR')
