const {test}=require('node:test');const a=require('node:assert/strict');const fs=require('node:fs');
const read=n=>JSON.parse(fs.readFileSync(__dirname+'/'+n));const updated=read('update.json'),old=read('rollback.json');
function fixture(n){const s={};const fn=new Function('msg','context','node',n.func);return msg=>fn(msg,{get:k=>s[k],set:(k,v)=>s[k]=v},{id:n.id});}
test('TV ON/OFF provenance returns with same ID and original legacy payload',()=>{
 for(const [value,index] of [['on',1],['off',2]]){
 const c={schema:'domopi.semantic_command.v1',entity_id:'lights.tv.power',actor:'utente',stage:'routed',routed_topic:'zara/domotics/lights/tvlamp',requested_state:value==='on',command_id:'test:1'};
 const r=fixture(updated[0])({topic:c.routed_topic,payload:value,_semantic_command:c});
 const cmd=r[value==='on'?5:6];const reply=fixture(updated[index])({...cmd,payload:{status:'OK'}});
 a.deepEqual(reply[0],{topic:c.routed_topic,payload:value});
 const e=JSON.parse(reply[1][0].payload);a.equal(e.command_id,c.command_id);a.equal(e.stage,'send_invoked');a.equal(e.physical_confirmation,false);
 }
});
test('legacy and other devices preserve all dispatch outputs and RBE',()=>{
 const x=fixture(old[0]),y=fixture(updated[0]);
 for(const device of ['tvlamp','livinglamp','shelflamp','readinglight','prolunga','poolpump','gate_1','unknown'])for(const payload of ['on','on','off','bad']){
 const msg={topic:'zara/domotics/lights/'+device,payload};a.deepEqual(y(msg),x(msg));
 }
});
test('missing, mismatched or failed receipt does not fabricate attribution',()=>{
 for(const c of [undefined,{}, {schema:'domopi.semantic_command.v1',entity_id:'lights.tv.power',actor:'utente',stage:'dispatched',command_id:'x',requested_state:false}]){
 const r=fixture(updated[1])({payload:{status:'OK'},_semantic_command:c});a.equal(r[1],null);a.equal(r[0].payload,'on');
 }
});
test('original wiring preserved, only telemetry exits added',()=>{
 a.deepEqual(updated[0].wires,old[0].wires);
 for(let i=1;i<3;i++){a.deepEqual(updated[i].wires[0],old[i].wires[0]);a.equal(updated[i].outputs,2);a.deepEqual(updated[i].wires[1],[updated[3].id]);}
});
