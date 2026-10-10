import json,pathlib,copy,hashlib,sys
root=pathlib.Path(__file__).resolve().parent
raw=pathlib.Path(sys.argv[1]).read_bytes();ns=json.loads(raw);by={n['id']:n for n in ns};changed={};added=[]
def edit(id):
 if id not in changed:changed[id]=copy.deepcopy(by[id])
 return changed[id]
def ident(s):
 id=hashlib.sha256(('domopi-all-lights:'+s).encode()).hexdigest()[:16];assert id not in by;return id
def fn(id,z,name,code,wires,x=1100,y=200):
 n={'id':id,'type':'function','z':z,'name':name,'func':code,'outputs':len(wires),'noerr':0,'initialize':'','finalize':'','libs':[],'x':x,'y':y,'wires':wires};added.append(n);return n
# Verified direct sources only. Scene sources are already annotated separately.
ui=['da2e1f45.33e288','7a84beaa.30de78','d17c748ec892c494','b60e2fe7.7ab5d','cd1ccb1e.905148','a0b2b287.eaf528','c93d56eb.b0f16','2f7737f3.e2ff58']
alexa=['f2e1ae60.c1ef28','e8580997.ad8b08','cae7c402d00b5af1','34e25134.04530e','fda64996.64402','886af877.7a612','f83d163a.c794c','951aa08f.5ccf38']
for source in ui+alexa:
 n=edit(source)
 if source in ui:assert n['passthru'] is False
 id=ident(source);wires=copy.deepcopy(n['wires']);assert len(wires)==1
 n['wires']=[[id]]
 code=(root/'origin.js').read_text().replace('ORIGIN','nodered_dashboard' if source in ui else 'alexa')
 fn(id,n['z'],'Light origin '+source,code,wires,n.get('x',0)+150,n.get('y',0)+60)
# Motion rule outputs: mark only the action edge, after filters/timers.
for source,target in [('976625b3.d8ed6','39b279ab.389a96'),('d9cf1037.11562','39b279ab.389a96'),('562feb36.e6289c','8dcfcfe5.990fd8'),('80e44a93.689f1','8dcfcfe5.990fd8'),('8fecb2d0.f05288','5f9ae3f3.7946d4'),('aa142b2c.36921','5f9ae3f3.7946d4')]:
 n=edit(source);id=ident('motion:'+source)
 assert any(target in port for port in n['wires'])
 n['wires']=[[id if t==target else t for t in port] for port in n['wires']]
 code=(root/'origin.js').read_text().replace('ORIGIN','motion_rule').replace("actor:'utente'","actor:'automazione'")
 fn(id,n['z'],'Light motion origin '+source,code,[[target]],n.get('x',0)+150,n.get('y',0)+60)
# Preserve metadata on the existing internal living command route only.
n=edit('ea8307d48995ca93');a="['tvlamp','shelflamp','readinglight'].includes(device)";assert a in n['func'];n['func']=n['func'].replace(a,"['tvlamp','shelflamp','readinglight','livinglamp'].includes(device)")
# Sink-specific adapters do not bypass any gate or change payloads.
rm=[('11c056bd.ce92c9','lights.living.power',True),('cb412a79.1d2ff','lights.living.power',False),('4f3d0b05.4a90dc','lights.tv.power',True),('7983c8a2.9ff7b8','lights.tv.power',False),('00a926dcd53cc068','lights.reading.power',True),('2ef73b3e9ebd0550','lights.reading.power',False),('81c3c978.57ef2','pool.deck.power',True),('9358b53b.2ed9b8','pool.deck.power',False),('3f2b8514.11af32','pool.water.power',True),('19fe9f8b.60f83','pool.water.power',False)]
mqtt=[('a4999728.3f7868','lights.laundry.power'),('140c8f41.289a81','lights.service_entrance.power'),('dd2082bb.de78e8','lights.porch_entrance.power')]
pubs={}
def publisher(z):
 if z not in pubs:
  id=ident('publisher:'+z);pubs[z]=id;added.append({'id':id,'type':'mqtt out','z':z,'name':'Light semantic evidence','topic':'','qos':'','retain':'','broker':'3ed6302a.5af928','x':1500,'y':200,'wires':[]})
 return pubs[z]
def intercept(target,entity,state=None,is_mqtt=False):
 id=ident('sink:'+target);targetnode=by[target]
 # Also consider new marker outputs created above.
 for parent in list(by.values())+added[:]:
  if any(target in port for port in parent.get('wires',[])):
   p=edit(parent['id']) if parent['id'] in by else parent
   p['wires']=[[id if t==target else t for t in port] for port in p['wires']]
 code=(root/'adapter.js').read_text().replace('ENTITY',entity).replace('STATE',"msg.payload === 'on'" if is_mqtt else str(state).lower())
 if is_mqtt:
  code+="""\nconst c=msg._semantic_command;
let evidence=null;
if (c && ['on','off'].includes(msg.payload)) {
 const e={...c,stage:'publisher_input',observed_at_ms:Date.now(),evidence:'before_mqtt_out',execution_confirmed:false,physical_confirmation:false};
 evidence={topic:'zara/interface/semantic/v1/publisher_inputs/'+c.entity_id+'/stat',payload:JSON.stringify(e),qos:1,retain:true};
}
delete msg._semantic_command;
return [msg,evidence];"""
  wires=[[target],[publisher(targetnode['z'])]]
 else:code+='\nreturn msg;';wires=[[target]]
 fn(id,targetnode['z'],'Light evidence '+entity,code,wires)
for target,entity,state in rm:intercept(target,entity,state)
for target,entity in mqtt:intercept(target,entity,is_mqtt=True)
reports=[('bccbd37f.6e4cd8','lights.living.power',True),('6197926c.612a8c','lights.living.power',False),('bfa134b2.bcd84','pool.deck.power',True),('6586d147.437dc','pool.deck.power',False),('55470375.8b265c','pool.water.power',True),('1ec04f1a.79db19','pool.water.power',False)]
# Reuse verified report contract from library; living receipt is only after second RM.
for id,entity,state in reports:
 n=edit(id);assert n['func'].strip()==f'msg.payload = "{"on" if state else "off"}";\nreturn msg;'
 template=by['f7bc8ba8.270b6' if state else '18386049.c296a8']['func']
 n['func']=template.replace('lights.libreria.power',entity);n['outputs']=2;n['wires'].append([publisher(n['z'])])
# Allow direct source/scene provenance for the TV/reading reports too.
for id in ['8f921a5e.1766f','ed8c7e3e.e2c32','a082b270d39049dc','1d425ade7f5a82ae']:
 n=edit(id);n['func']=n['func'].replace("c.stage === 'dispatched'","['dispatched','source_selected'].includes(c.stage)").replace('origin:c.origin,','origin:c.origin,scene:c.scene||null,')
for name,data in [('update.json',list(changed.values())+added),('rollback.json',[by[id] for id in changed])]: (root/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
(root/'manifest.json').write_text(json.dumps({'base_sha256':hashlib.sha256(raw).hexdigest(),'modified_ids':list(changed),'added_ids':[n['id'] for n in added]},indent=2)+'\n')
