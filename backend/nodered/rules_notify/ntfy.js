// RULES-ENGINE.2 (2026-10-17): every rule event becomes a phone notification through ntfy on .20
// (backend/ntfy, channel domopi-regole). JSON publish, token loaded from a protected file.
// RULES-ENGINE.3 (2026-10-18): timed commands created active say so.
let e;
try { e = JSON.parse(msg.payload); } catch (err) { return null; }
const token = flow.get('ntfyToken');
if (!token) { node.warn('ntfy: token non caricato, notifica non inviata'); return null; }
const at = s => { const d = new Date(s); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
const text = e.text || 'regola';
const kinds = {
    fired: ['Regola intervenuta', `${text}. Comando inviato.`, ['fire'], 4],
    would_fire: ['Regola in prova', `Avrebbe agito: ${text}. Nessun comando inviato.`, ['test_tube'], 3],
    blocked: ['Regola bloccata', `${text}: ${e.reason || 'comando non ammesso ora'}${e.testing ? ' (in prova)' : ''}.`, ['no_entry'], 4],
    created: e.active ? ['Comando programmato', `${text}. Attivo subito.`, ['alarm_clock'], 3]
                      : ['Regola creata', `${text}. In prova fino alle ${e.test_until ? at(e.test_until) : '—'} di domani: annuncia senza agire.`, ['white_check_mark'], 3],
    activated: ['Regola attiva', `${text}. Fine della prova.`, ['arrow_forward'], 3],
    deleted: ['Regola cancellata', text, ['wastebasket'], 2],
    rejected: ['Regola rifiutata', e.reason || 'regola non valida', ['warning'], 3],
};
const k = kinds[e.kind];
if (!k) return null;
msg.url = 'http://127.0.0.1:2586/';
msg.method = 'POST';
msg.headers = {Authorization: 'Bearer ' + token, 'Content-Type': 'application/json'};
msg.payload = JSON.stringify({topic: 'domopi-regole', title: k[0], message: k[1], tags: k[2], priority: k[3]});
return msg;
