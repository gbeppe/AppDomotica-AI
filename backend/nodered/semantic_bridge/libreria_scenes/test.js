const {test}=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs');
const u=JSON.parse(fs.readFileSync(__dirname+'/update.json')),old=JSON.parse(fs.readFileSync(__dirname+'/rollback.json'));const by=Object.fromEntries(u.map(n=>[n.id,n]));const oldIds=new Set(old.map(n=>n.id));
function run(n,msg){return new Function('msg','context','node',n.func)(msg,{get:()=>0,set:()=>{}},{id:n.id,warn:()=>{}});}
test('all four app scenes preserve original operational output and carry provenance',()=>{
 const id='937d52b7b946ba3b';for(const scene of ['tv','sleep','all_on','all_off']){
 const original=run(old.find(n=>n.id===id),{payload:scene});const result=run(by[id],{payload:scene});
 a.deepEqual(result.map(m=>m?{payload:m.payload}:null),original);
 a.equal(result.find(Boolean)._semantic_scene.scene,scene);
 const retained=run(by[id],{payload:scene,retain:true});a.equal(retained.find(Boolean)._semantic_scene,undefined);
 }
});
test('source markers keep payload; both library adapters propagate scene to RM receipt',()=>{
 for(const source of u.filter(n=>n.name?.startsWith('Scene provenance'))){
 const msg=run(source,{payload:'on'});a.equal(msg.payload,'on');a.equal(msg._semantic_scene.actor,'utente');
 for(const state of [true,false]){
 const adapter=u.find(n=>n.name==='Libreria scene '+(state?'ON':'OFF'));
 const branch=run(adapter,structuredClone(msg));a.equal(branch.payload,'on');a.equal(branch._semantic_command.requested_state,state);
 const receipt=run(by[state?'f7bc8ba8.270b6':'18386049.c296a8'],{...branch,payload:{status:'OK'}});
 const e=JSON.parse(receipt[1][0].payload);a.equal(e.scene,msg._semantic_scene.scene);a.equal(e.physical_confirmation,false);
 }
 }
});
test('all old wires preserved through single pass-through markers, no fan-out added',()=>{
 for(const n of old){const current=by[n.id];a.equal(current.wires.length,n.wires.length);
 const resolved=current.wires.map(port=>port.map(id=>oldIds.has(id)||!by[id]?id:by[id].wires[0][0]));a.deepEqual(resolved,n.wires,n.id);
 }
});
test('direct user/automation provenance remains unchanged without scene',()=>{
 const adapter=u.find(n=>n.name==='Libreria scene OFF');const msg={payload:'true',_semantic_command:{actor:'automazione',command_id:'lg'}};
 a.deepEqual(run(adapter,structuredClone(msg)),msg);
});
