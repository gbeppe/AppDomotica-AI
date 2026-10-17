// HOUSE-ALERTS.1: alert -> ntfy JSON publish on domopi-casa (token loaded from a protected file).
const token = flow.get('ntfyToken');
if (!token) { node.warn('ntfy: token non caricato, avviso non inviato'); return null; }
const a = msg.payload;
msg.url = 'http://127.0.0.1:2586/';
msg.method = 'POST';
msg.headers = {Authorization: 'Bearer ' + token, 'Content-Type': 'application/json'};
msg.payload = JSON.stringify({topic: 'domopi-casa', title: a.title, message: a.message, tags: a.tags, priority: a.priority});
return msg;
