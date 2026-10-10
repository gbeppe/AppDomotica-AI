const {test}=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs');
const u=JSON.parse(fs.readFileSync(__dirname+'/update.json')),old=JSON.parse(fs.readFileSync(__dirname+'/rollback.json'));
function run(n,msg){return new Function('msg','context','node',n.func)(msg,{get:()=>0,set:()=>{}},{id:n.id,warn:()=>{}});}
test('all changed function bodies compile',()=>{for(const n of u.filter(n=>n.type==='function'))new Function('msg','context','node',n.func);});
test('Alexa is automation while dashboard sources remain user',()=>{
 for(const n of u.filter(n=>n.type==='function' && (n.func.includes("origin:'alexa'")||n.func.includes("origin:'nodered_dashboard'")))){
  if(!/^(Light origin|Scene provenance|Libreria origin)/.test(n.name))continue;
  const m=run(n,{payload:'on'});const c=m._semantic_scene||m._semantic_origin||m._semantic_command;
  a(c,n.id);a.equal(c.actor,c.origin==='alexa'?'automazione':'utente');a.equal(c.actor_policy,'dashboard_only_v2');
 }
});
test('shared all-off button distinguishes dashboard from editor passthrough',()=>{
 const n=u.find(n=>n.name==='Scene provenance nodered_ui_or_editor all_off');
 a.equal(run(n,{payload:'on',socketid:'session'})._semantic_scene.actor,'utente');
 a.equal(run(n,{payload:'on'})._semantic_scene.actor,'automazione');
});
test('schedex annotation survives library adapter and RM receipt',()=>{
 const marker=u.find(n=>n.name==='Schedex actor policy');const adapter=u.find(n=>n.name==='Libreria scene OFF');
 const msg=run(adapter,run(marker,{payload:'off',topic:'auto'}));a.equal(msg.payload,'off');a.equal(msg.topic,'auto');
 const report=u.find(n=>n.id==='18386049.c296a8');const r=run(report,{...msg,payload:{status:'OK'}});
 const e=JSON.parse(r[1][0].payload);a.equal(e.actor,'automazione');a.equal(e.origin,'schedex');a.equal(e.actor_policy,'dashboard_only_v2');a.equal(e.physical_confirmation,false);
});
test('no operational wiring changes except pass-through schedex marker',()=>{
 for(const n of old){if(n.id!=='41bab9be.81c0e8')a.deepEqual(u.find(x=>x.id===n.id).wires,n.wires);}
});
