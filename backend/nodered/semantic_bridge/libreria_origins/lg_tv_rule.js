// Only annotate this verified source edge. Never alter its payload or routing.
const valid = msg.payload === 'true';
delete msg._semantic_command;
if (valid) {
 const time = Date.now();
 const seq = (context.get('origin_seq') || 0) + 1;
 context.set('origin_seq', seq);
 msg._semantic_command = {
  schema:'domopi.semantic_command.v1',entity_id:'lights.libreria.power',
  command_id:node.id+':'+time+':'+seq,actor:'automazione',origin:'lg_tv_rule',
  attribution:'source_edge',stage:'source_selected',requested_state:false,
  observed_at_ms:time,source_retained:msg.retain===true,
  execution_confirmed:false,physical_confirmation:false,publisher_authenticated:false
 };
}
return msg;
