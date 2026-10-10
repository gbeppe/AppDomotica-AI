import json,pathlib,copy,hashlib,sys
root=pathlib.Path(__file__).resolve().parent
base=json.loads(pathlib.Path(sys.argv[1]).read_text());ns=base['flows'] if isinstance(base,dict) else base
by={n['id']:n for n in ns};changed={};added=[]
def edit(id):
 if id not in changed:changed[id]=copy.deepcopy(by[id])
 return changed[id]
def identifier(name):
 id=hashlib.sha256(('domopi-scene:'+name).encode()).hexdigest()[:16];assert id not in by;return id
def marker(source,target,origin,scene):
 n=edit(source);assert target in n['wires'][0];id=identifier(source)
 n['wires'][0]=[id if t==target else t for t in n['wires'][0]]
 expression="msg.payload === 'on' ? 'all_on' : 'all_off'" if scene=='all' else repr(scene)
 valid="['on','off'].includes(msg.payload)" if scene=='all' else "msg.payload === 'on'"
 code="""delete msg._semantic_scene;
if (VALID) {
 const sequence=(context.get('seq')||0)+1;context.set('seq',sequence);
 msg._semantic_scene={actor:'utente',origin:'ORIGIN',scene:SCENE,
  command_id:node.id+':'+Date.now()+':'+sequence,attribution:'source_edge'};
}
return msg;""".replace('VALID',valid).replace('ORIGIN',origin).replace('SCENE',expression)
 added.append({'id':id,'type':'function','z':n['z'],'name':'Scene provenance '+origin+' '+scene,'func':code,'outputs':1,'noerr':0,'initialize':'','finalize':'','libs':[],'x':n.get('x',0)+180,'y':n.get('y',0)+70,'wires':[[target]]})
# UI: passthrough all-off also receives an editor-manual inject, so label accurately.
for args in [('71aeaed6.a4d5b8','63eaa703.fd98c','nodered_dashboard','tv'),('53b3ae4.c08ccd','a901336b.d16cb8','nodered_dashboard','sleep'),('a52920f.8a0476','f05d5124.911a38','nodered_dashboard','all_on'),('e5c6a02.cf0d46','473f5068.10fbb8','nodered_ui_or_editor','all_off'),('5b048bd8.9c1794','851666d5.0cc428','alexa','tv'),('7b2e5e45.6a8be8','baf2d8b.17385a8','alexa','sleep'),('7afa36cd.02d79','e761004.c0c1a8','alexa','all')]:marker(*args)
n=edit('937d52b7b946ba3b');assert n['func'].count('return outputs;')==1
n['func']=n['func'].replace('return outputs;',"""// Preserve only explicit app scene provenance. Operational payloads unchanged.
if (msg.retain !== true) {
 const sequence=(context.get('semantic_scene_seq')||0)+1;
 context.set('semantic_scene_seq',sequence);
 for(const out of outputs) if(out) out._semantic_scene={
  actor:'utente',origin:'app_interface',scene,
  command_id:node.id+':'+Date.now()+':'+sequence,attribution:'interface_contract'};
}
return outputs;""")
# Library-only adapters: enrich after all scene gates/delays, immediately before RM.
for target,state in [('f5f14a57.6f51a8',True),('375cd5f5.f38faa',False)]:
 id=identifier(target)
 for parent in ns:
  if any(target in port for port in parent.get('wires',[])):
   n=edit(parent['id']);n['wires']=[[id if t==target else t for t in port] for port in n['wires']]
 code="""const s=msg._semantic_scene;
if (s && s.actor==='utente' && typeof s.command_id==='string' &&
 ['tv','sleep','all_on','all_off'].includes(s.scene)) {
 msg._semantic_command={schema:'domopi.semantic_command.v1',
  entity_id:'lights.libreria.power',actor:s.actor,origin:s.origin,
  attribution:s.attribution,scene:s.scene,command_id:s.command_id+':libreria:STATE',
  requested_state:STATE,stage:'source_selected',observed_at_ms:Date.now(),
  execution_confirmed:false,physical_confirmation:false,publisher_authenticated:false};
}
delete msg._semantic_scene;
return msg;""".replace('STATE',str(state).lower())
 added.append({'id':id,'type':'function','z':by[target]['z'],'name':'Libreria scene '+('ON' if state else 'OFF'),'func':code,'outputs':1,'noerr':0,'initialize':'','finalize':'','libs':[],'x':1000,'y':400 if state else 800,'wires':[[target]]})
for id in ['f7bc8ba8.270b6','18386049.c296a8']:
 n=edit(id);assert 'origin:c.origin,' in n['func'];n['func']=n['func'].replace('origin:c.origin,','origin:c.origin,scene:c.scene||null,')
for file,value in [('update.json',list(changed.values())+added),('rollback.json',[by[id] for id in changed])]: (root/file).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
(root/'manifest.json').write_text(json.dumps({'modified_ids':list(changed),'added_ids':[n['id'] for n in added]},indent=2)+'\n')
