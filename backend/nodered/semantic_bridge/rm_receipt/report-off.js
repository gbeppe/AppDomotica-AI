const c = msg._semantic_command;
const rm = msg.payload;
let telemetry = null;
// RM reports OK after invoking sendData, without waiting for a lamp acknowledgment.
if (c && c.schema === 'domopi.semantic_command.v1' &&
    c.entity_id === 'lights.tv.power' && c.actor === 'utente' &&
    c.stage === 'dispatched' && typeof c.command_id === 'string' &&
    c.requested_state === false && rm && rm.status === 'OK') {
    const e = {schema:'domopi.semantic_command.v1',entity_id:c.entity_id,
        command_id:c.command_id,actor:c.actor,origin:c.origin,
        attribution:c.attribution,requested_state:c.requested_state,
        stage:'send_invoked',observed_at_ms:Date.now(),
        evidence:'rm_output_after_sendData',execution_confirmed:false,
        physical_confirmation:false,publisher_authenticated:false};
    telemetry = [
        {topic:'zara/interface/semantic/v1/rm_receipts/'+c.entity_id+'/stat',
         payload:JSON.stringify(e),qos:1,retain:true},
        {topic:'zara/interface/semantic/v1/events',
         payload:JSON.stringify({...e,schema:'domopi.semantic_event.v1',
             event_id:c.command_id+':rm',kind:'rm_send_invoked'}),qos:1,retain:false}
    ];
}
delete msg._semantic_command;
msg.payload = 'off';
return [msg, telemetry];
