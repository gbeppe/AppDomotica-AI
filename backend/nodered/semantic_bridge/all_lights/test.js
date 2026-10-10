const {test}=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs');
const u=JSON.parse(fs.readFileSync(__dirname+'/update.json')),old=JSON.parse(fs.readFileSync(__dirname+'/rollback.json'));
const by=Object.fromEntries(u.map(n=>[n.id,n])),oldIds=new Set(old.map(n=>n.id));
function run(n,msg){const s={};return new Function('msg','context','node',n.func)(msg,{get:k=>s[k],set:(k,v)=>s[k]=v},{id:n.id});}
function expand(id,seen=new Set()){
 if(oldIds.has(id)||!by[id])return [id];a(!seen.has(id),'new cycle');seen.add(id);
 return by[id].wires[0].flatMap(t=>expand(t,new Set(seen)));
}
test('operational wiring preserved through adapters without extra actuator fan-out',()=>{
 for(const n of old){const now=by[n.id];for(let i=0;i<n.wires.length;i++)a.deepEqual(now.wires[i].flatMap(id=>expand(id)),n.wires[i],n.id);}
});
test('all direct source markers preserve payload and assign expected actor',()=>{
 for(const n of u.filter(n=>n.name?.startsWith('Light origin ')||n.name?.startsWith('Light motion origin '))){
 for(const p of ['on','off']){const m=run(n,{payload:p,topic:'x'});a.equal(m.payload,p);a.equal(m.topic,'x');a.equal(m._semantic_origin.actor,n.name.includes('motion')?'automazione':'utente');}
 a.equal(run(n,{payload:'bad'})._semantic_origin,undefined);
 }
});
test('all RM report paths keep entity, command ID, stage and legacy scalar',()=>{
 for(const adapter of u.filter(n=>n.name?.startsWith('Light evidence ') && n.outputs===1)){
 const entity=adapter.name.slice('Light evidence '.length);
 const out=run(adapter,{payload:'unchanged',_semantic_origin:{actor:'utente',origin:'test',attribution:'source_edge',command_id:'source'}});
 a.equal(out.payload,'unchanged');a.equal(out._semantic_command.entity_id,entity);
 const expected=out._semantic_command.requested_state;
 const report=u.find(n=>n.type==='function' && n.func.includes("c.entity_id === '"+entity+"'") && n.func.includes('c.requested_state === '+expected));
 a(report,entity);const r=run(report,{...out,payload:{status:'OK'}});
 a.equal(r[0].payload,expected?'on':'off');const e=JSON.parse(r[1][0].payload);a.equal(e.entity_id,entity);a.equal(e.command_id,out._semantic_command.command_id);a.equal(e.physical_confirmation,false);
 }
});
test('Shelly publisher input separates user and motion without claiming delivery',()=>{
 for(const n of u.filter(n=>n.name?.startsWith('Light evidence ')&&n.outputs===2))for(const actor of ['utente','automazione']){
 const r=run(n,{payload:'off',_semantic_origin:{actor,origin:'test',command_id:'source'}});
 a.equal(r[0].payload,'off');a.equal(r[0]._semantic_command,undefined);
 const e=JSON.parse(r[1].payload);a.equal(e.actor,actor);a.equal(e.stage,'publisher_input');a.equal(e.execution_confirmed,false);
 a.equal(run(n,{payload:'off'})[1],null);
 }
});
test('scene propagation is entity-specific and no origin remains unknown',()=>{
 for(const n of u.filter(n=>n.name?.startsWith('Light evidence ')&&n.outputs===1)){
 const plain=run(n,{payload:'on'});a.equal(plain._semantic_command,undefined);
 const scene=run(n,{payload:'on',_semantic_scene:{actor:'utente',origin:'app_interface',scene:'all_off',command_id:'scene'}});
 a.equal(scene._semantic_command.scene,'all_off');a.equal(scene._semantic_scene,undefined);
 }
});
