const {test}=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs');
const u=JSON.parse(fs.readFileSync(__dirname+'/update.json')),old=JSON.parse(fs.readFileSync(__dirname+'/rollback.json'));
function run(n,msg){const s={};return new Function('msg','context','node',n.func)(msg,{get:k=>s[k],set:(k,v)=>s[k]=v},{id:n.id});}
for(const origin of ['nodered_dashboard','alexa','lg_tv_rule'])test(origin+' keeps payload and provenance through RM',()=>{
 const marker=u.find(n=>n.name==='Libreria origin '+origin);
 for(const p of origin==='lg_tv_rule'?['true']:['on','off']){
 const m=run(marker,{payload:p,topic:'original',retain:true});a.equal(m.payload,p);a.equal(m.topic,'original');
 const expected=origin==='lg_tv_rule'?'automazione':'utente';a.equal(m._semantic_command.actor,expected);
 const isOn=p==='on';const report=u.find(n=>n.id===(isOn?'f7bc8ba8.270b6':'18386049.c296a8'));
 const result=run(report,{...m,payload:{status:'OK'}});a.equal(result[0].payload,isOn?'on':'off');a.equal(result[0]._semantic_command,undefined);
 const e=JSON.parse(result[1][0].payload);a.equal(e.actor,expected);a.equal(e.origin,origin);a.equal(e.command_id,m._semantic_command.command_id);a.equal(e.physical_confirmation,false);
 }
});
test('invalid inputs do not gain attribution; original UI side branch is unchanged',()=>{
 for(const n of u.filter(n=>n.name?.startsWith('Libreria origin ')))a.equal(run(n,{payload:'bad'})._semantic_command,undefined);
 const id='ad32e56b.84cfe';a.equal(u.find(n=>n.id===id).wires[0][1],old.find(n=>n.id===id).wires[0][1]);
 for(const id of ['f7bc8ba8.270b6','18386049.c296a8'])a.deepEqual(u.find(n=>n.id===id).wires,old.find(n=>n.id===id).wires);
});
test('existing app path still works; missing provenance remains unknown',()=>{
 const report=u.find(n=>n.id==='f7bc8ba8.270b6');
 a.equal(run(report,{payload:{status:'OK'}})[1],null);
 const r=run(report,{payload:{status:'OK'},_semantic_command:{schema:'domopi.semantic_command.v1',entity_id:'lights.libreria.power',actor:'utente',stage:'dispatched',command_id:'app',requested_state:true}});
 a.equal(JSON.parse(r[1][0].payload).command_id,'app');
});
