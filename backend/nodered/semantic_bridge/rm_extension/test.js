const {test}=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs');
const u=JSON.parse(fs.readFileSync(__dirname+'/update.json')),o=JSON.parse(fs.readFileSync(__dirname+'/rollback.json'));
function run(n,msg){return new Function('msg','context','node',n.func)(msg,{get:()=>undefined,set:()=>{}},{id:n.id});}
test('libreria and reading ON/OFF carry command ID and preserve legacy report',()=>{
 for(const [device,entity,slot,index] of [['shelflamp','lights.libreria.power',1,1],['readinglight','lights.reading.power',7,3]])for(const on of [true,false]){
 const c={schema:'domopi.semantic_command.v1',entity_id:entity,actor:'utente',stage:'routed',command_id:'proof',requested_state:on,routed_topic:'zara/domotics/lights/'+device};
 const msg={topic:c.routed_topic,payload:on?'on':'off',_semantic_command:c};const dispatched=run(u[0],msg)[slot+(on?0:1)];
 const result=run(u[index+(on?0:1)],{...dispatched,payload:{status:'OK'}});
 a.deepEqual(result[0],{topic:msg.topic,payload:msg.payload});const e=JSON.parse(result[1][0].payload);a.equal(e.command_id,'proof');a.equal(e.entity_id,entity);a.equal(e.physical_confirmation,false);
 }
});
test('legacy reports do not invent an actor and existing wires remain',()=>{
 for(let i=1;i<u.length;i++){a.equal(run(u[i],{payload:{status:'OK'}})[1],null);a.deepEqual(u[i].wires[0],o[i].wires[0]);}
 a.deepEqual(u[0].wires,o[0].wires);
});
