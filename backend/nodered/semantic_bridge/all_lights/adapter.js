const o=msg._semantic_scene || msg._semantic_origin;
if (o && ['utente','automazione'].includes(o.actor) && typeof o.command_id==='string') {
 msg._semantic_command={schema:'domopi.semantic_command.v1',entity_id:'ENTITY',
  actor:o.actor,origin:o.origin,scene:o.scene||null,attribution:o.attribution,
  command_id:o.command_id+':ENTITY:'+String(STATE),requested_state:STATE,
  stage:'source_selected',observed_at_ms:Date.now(),execution_confirmed:false,
  physical_confirmation:false,publisher_authenticated:false};
}
delete msg._semantic_scene;
delete msg._semantic_origin;
