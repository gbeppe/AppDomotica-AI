delete msg._semantic_origin;
if (['on','off'].includes(msg.payload)) {
 const seq=(context.get('seq')||0)+1;context.set('seq',seq);
 msg._semantic_origin={actor:'utente',origin:'ORIGIN',attribution:'source_edge',
  command_id:node.id+':'+Date.now()+':'+seq};
}
return msg;
