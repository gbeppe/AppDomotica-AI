# Verifica reale degli estremi energetici giornalieri

Verifica eseguita il 13 settembre 2026 dopo il limite osservato nell'app sulla
domanda «Negli ultimi 6 mesi quale è stato il giorno col maggior prelievo dalla
rete elettrica?».

## Scelta e architettura

È stato aggiunto il tool chiuso `energy_daily_extreme`. Groq sceglie soltanto
metrica, date e modalità `maximum`/`minimum`; non riceve campioni né calcola il
risultato. Il backend legge il feed EmonCMS validato una volta, assegna i campioni
ai giorni di calendario `Europe/Rome`, integra il solo verso richiesto e confronta
esclusivamente i giorni con copertura completa.

La presentazione successiva usa anche tutti i valori parziali EmonCMS: mostra
separatamente il massimo fra giorni completi e il massimo dell'energia osservata
fra tutti i giorni. Per un giorno incompleto il valore è un limite minimo e la
percentuale è chiamata **copertura dati**, mai certezza. Senza un limite fisico o
un modello validato per gli intervalli mancanti, il massimo assoluto viene
dichiarato indeterminato quando altri giorni parziali potrebbero superarlo.

Questa soluzione evita circa 184 operazioni nel piano e mantiene invariati il
limite di sei operazioni, il protocollo read-only, le credenziali e il confine fra
planner probabilistico e calcoli deterministici. Non sono stati installati nuovi
tool, SDK o pacchetti: restano sufficienti Python 3.9 standard library, Groq,
Tailscale Serve e i servizi systemd già documentati.

## Verifiche prima del deployment

- 63 test backend superati localmente;
- prova sintetica su 184 giorni: 184 giorni eleggibili, 53 blocchi di lettura;
- compilazione dei file modificati e 63 test superati su `.20`, Python 3.9.2,
  come utente `house-ai`;
- attivazione atomica della release `20260913-daily-extreme`, revisione
  `16e47fa`; release `20260913-tls` conservata per rollback.

## Prova con sorgenti reali e TLS

Percorso verificato:

`client HTTPS` → `Tailscale Serve` → `backend .20` → `gateway .20` → `Groq` →
`backend .20` → `EmonCMS .15`.

Il piano restituito dal modello è stato:

```json
{
  "id": "op1",
  "tool": "energy_daily_extreme",
  "metric": "grid_import_kwh",
  "start": "2026-03-13",
  "end": "2026-09-13",
  "extremum": "maximum"
}
```

Risultato osservato, senza riportare credenziali o campioni grezzi:

- HTTP 200 tramite `https://domopi.tailf30ba8.ts.net`;
- stato `partial`;
- 165 giorni con copertura completa confrontati;
- 19 giorni incompleti esclusi;
- massimo fra i giorni eleggibili: **11 settembre 2026, 3,75 kWh**;
- 53 richieste EmonCMS a blocchi, intervallo sorgente 30 secondi.

`partial` non indica un errore del calcolo: segnala che il primo confronto vale
per i 165 giorni completi. I 19 giorni incompleti partecipano al secondo confronto
con la sola energia effettivamente osservata e la propria copertura; i buchi non
vengono colmati o stimati. Uno di essi potrebbe quindi avere avuto un valore reale
superiore. Il valore è una stima operativa dai
campioni del feed 305, non una misura fiscale del contatore.

## Replica e rollback

Una replica futura non richiede configurazioni aggiuntive rispetto a
[`GATEWAY_MODELLO.md`](GATEWAY_MODELLO.md): copiare una release che includa il
commit `16e47fa` o successivo, eseguire compilazione e test con il Python del
server, quindi spostare il symlink `current` e riavviare gateway e backend.

Per rollback su `.20`, ripuntare `current` a
`/opt/domopi-house-ai/releases/20260913-tls` e riavviare entrambi i servizi. Non
occorre modificare Tailscale, API key Groq, token applicativo o chiave EmonCMS.
