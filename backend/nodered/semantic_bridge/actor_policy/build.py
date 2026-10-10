import json,pathlib,copy,hashlib,sys
root=pathlib.Path(__file__).resolve().parent
ns=json.loads(pathlib.Path(sys.argv[1]).read_text());by={n['id']:n for n in ns};changed={};added=[]
def edit(n):
 if n['id'] not in changed:changed[n['id']]=copy.deepcopy(n)
 return changed[n['id']]
for n in ns:
 if n.get('type')!='function':continue
 s=n['func'];t=s
 if "origin:'alexa'" in t or "origin: 'alexa'" in t:
  t=t.replace("actor:'utente'","actor:'automazione'").replace("actor: 'utente'","actor: 'automazione'")
 if 'nodered_ui_or_editor' in t:
  t=t.replace("actor:'utente'", "actor:(typeof msg.socketid==='string' && msg.socketid.length>0)?'utente':'automazione'")
  t=t.replace("origin:'nodered_ui_or_editor'", "origin:(typeof msg.socketid==='string' && msg.socketid.length>0)?'nodered_dashboard':'editor_manual'")
 if n.get('name','').startswith('Libreria scene '):
  t=t.replace('const s=msg._semantic_scene;', 'const s=msg._semantic_scene || msg._semantic_origin;')
  t=t.replace("s.actor==='utente'", "['utente','automazione'].includes(s.actor)")
  t=t.replace("['tv','sleep','all_on','all_off'].includes(s.scene)", "(s.scene == null || ['tv','sleep','all_on','all_off'].includes(s.scene))")
  t=t.replace('delete msg._semantic_scene;', 'delete msg._semantic_scene;\ndelete msg._semantic_origin;')
 if "rm_output_after_sendData" in t:
  t=t.replace("c.actor === 'utente'", "['utente','automazione'].includes(c.actor)")
 # Reports and metadata explicitly identify the current business classification.
 if any(x in t for x in ['_semantic_origin','_semantic_scene','_semantic_command']) and 'actor:' in t:
  t=t.replace('actor:',"actor_policy:'dashboard_only_v2',actor:")
 if t!=s:edit(n)['func']=t
scheduler=edit(by['41bab9be.81c0e8']);assert scheduler['wires']==[['daffc993.72e418']]
id=hashlib.sha256(b'domopi-dashboard-policy-schedex').hexdigest()[:16];assert id not in by
scheduler['wires']=[[id]]
code="""delete msg._semantic_origin;
if (['on','off'].includes(msg.payload)) {
 const seq=(context.get('seq')||0)+1;context.set('seq',seq);
 msg._semantic_origin={actor:'automazione',actor_policy:'dashboard_only_v2',
  origin:'schedex',attribution:'source_edge',command_id:node.id+':'+Date.now()+':'+seq};
}
return msg;"""
added.append({'id':id,'type':'function','z':scheduler['z'],'name':'Schedex actor policy','func':code,'outputs':1,'noerr':0,'initialize':'','finalize':'','libs':[],'x':270,'y':310,'wires':[['daffc993.72e418']]})
for name,obj in [('update.json',list(changed.values())+added),('rollback.json',[by[id] for id in changed])]: (root/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n')
(root/'manifest.json').write_text(json.dumps({'modified_ids':list(changed),'added_ids':[id],'policy':'dashboard_only_v2'},indent=2)+'\n')
