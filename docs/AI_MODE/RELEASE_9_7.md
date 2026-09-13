# Release 9.7 — AI Smart energia

La release 9.7 introduce la modalità AI Smart energia read-only mantenendo
disponibile la dashboard classica.

## Funzioni principali

- domande italiane scritte o dettate, con trascrizione modificabile e invio
  esplicito;
- dati correnti dal Digital Twin, storico da EmonCMS e consultazione dei quattro
  log Node-RED autorizzati;
- pianificazione Groq con catalogo chiuso e calcoli energetici deterministici;
- massimo/minimo giornaliero con uso distinto dei giorni completi e dei valori
  parziali, sempre accompagnati dalla copertura dati;
- collegamento privato TLS tramite Tailscale Serve;
- icona di diagnostica dello stack Smart con dettaglio di backend `.20`, Digital
  Twin, gateway, Groq, EmonCMS `.15` e log Node-RED;
- controlli e log diagnostici attivi soltanto mentre la schermata Smart è visibile.

## Installazione e dipendenze

L'APK non contiene token o chiavi. Per usare AI Smart occorrono Tailscale attivo,
URL `https://domopi.tailf30ba8.ts.net` e token backend inserito nella schermata.
Il backend coordinato è la release `/opt/domopi-house-ai/releases/20260913-stack-health`,
revisione `f2d50d8`.

Istruzioni complete: [SMART_ENERGIA.md](SMART_ENERGIA.md),
[GATEWAY_MODELLO.md](GATEWAY_MODELLO.md) e
[DIAGNOSTICA_STACK_SMART.md](DIAGNOSTICA_STACK_SMART.md).

## Verifica

- 66 test backend passati localmente e su Raspberry `.20`, Python 3.9.2;
- test JVM Android e build APK passati;
- 12 test strumentali mirati passati sull'emulatore Android 14;
- health dettagliato e transizione Groq `unknown` → `online` verificati tramite
  endpoint TLS reale;
- nessuna modifica a `.15`, Node-RED, Mosquitto o configurazione Tailscale.

L'APK allegato è una build **debug** destinata alle prove del progetto; è firmato
con la chiave debug e non costituisce un pacchetto Play Store di produzione.
