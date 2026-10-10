import copy,hashlib,json,pathlib,sys
root=pathlib.Path(__file__).resolve().parent
raw=pathlib.Path(sys.argv[1]).read_bytes();base=json.loads(raw);by={n['id']:n for n in base['flows']};updates=[];old=[]
routes=[('ad32e56b.84cfe',0,'c14c803c.86e27','utente','nodered_dashboard',None),('2e66d0dd.ac93d',0,'9d7004c9.a67e28','utente','alexa',None),('47b4e6deb69b994a',0,'5bd87c6ad192e8a0','automazione','lg_tv_rule',False)]
for id,port,target,actor,origin,fixed in routes:
 n=copy.deepcopy(by[id]);assert target in n['wires'][port]
 if origin=='nodered_dashboard':assert n['passthru'] is False
 old.append(copy.deepcopy(n));marker=hashlib.sha256(('domopi-origin:'+origin).encode()).hexdigest()[:16];assert marker not in by
 n['wires'][port]=[marker if t==target else t for t in n['wires'][port]];updates.append(n)
 code="""// Only annotate this verified source edge. Never alter its payload or routing.
const valid = VALID;
delete msg._semantic_command;
if (valid) {
 const time = Date.now();
 const seq = (context.get('origin_seq') || 0) + 1;
 context.set('origin_seq', seq);
 msg._semantic_command = {
  schema:'domopi.semantic_command.v1',entity_id:'lights.libreria.power',
  command_id:node.id+':'+time+':'+seq,actor:'ACTOR',origin:'ORIGIN',
  attribution:'source_edge',stage:'source_selected',requested_state:STATE,
  observed_at_ms:time,source_retained:msg.retain===true,
  execution_confirmed:false,physical_confirmation:false,publisher_authenticated:false
 };
}
return msg;
""".replace('VALID',"msg.payload === 'true'" if fixed is False else "msg.payload === 'on' || msg.payload === 'off'").replace('STATE','false' if fixed is False else "msg.payload === 'on'").replace('ACTOR',actor).replace('ORIGIN',origin)
 updates.append({'id':marker,'type':'function','z':n['z'],'name':'Libreria origin '+origin,'func':code,'outputs':1,'noerr':0,'initialize':'','finalize':'','libs':[],'x':n.get('x',0)+180,'y':n.get('y',0)+70,'wires':[[target]]})
 (root/(origin+'.js')).write_text(code)
for id in ['f7bc8ba8.270b6','18386049.c296a8']:
 n=copy.deepcopy(by[id]);old.append(copy.deepcopy(n))
 assert "c.actor === 'utente'" in n['func'] and "c.stage === 'dispatched'" in n['func']
 n['func']=n['func'].replace("c.actor === 'utente'", "['utente','automazione'].includes(c.actor)").replace("c.stage === 'dispatched'", "['dispatched','source_selected'].includes(c.stage)")
 n['func']=n['func'].replace('attribution:c.attribution,requested_state:c.requested_state,','attribution:c.attribution,requested_state:c.requested_state,\n        source_retained:c.source_retained===true,')
 updates.append(n)
for name,obj in [('update.json',updates),('rollback.json',old)]: (root/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
(root/'manifest.json').write_text(json.dumps({'base_revision':base['rev'],'base_sha256':hashlib.sha256(raw).hexdigest(),'modified_ids':[n['id'] for n in old],'added_ids':[n['id'] for n in updates if n['id'] not in by]},indent=2)+'\n')
