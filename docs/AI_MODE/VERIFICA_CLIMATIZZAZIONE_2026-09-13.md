# Verifica reale Smart climatizzazione — 13 settembre 2026

## Release e installazione

- commit distribuito: `ef1de8b`;
- release: `/opt/domopi-house-ai/releases/20260913-climate-reason`;
- symlink attivo: `/opt/domopi-house-ai/current`;
- Python del Raspberry `.20`: 3.9.2;
- controllo sintattico read-only e 69 test backend superati come utente
  `house-ai` prima dell'attivazione;
- `house-ai-gateway.service` e `house-ai-backend.service` attivi dopo il riavvio.

Il primo tentativo di `py_compile` come utente di servizio è stato correttamente
bloccato perché la release root-owned non consente di creare `__pycache__`. Il
symlink non era ancora stato cambiato. La verifica è stata ripetuta con
`ast.parse` e `python3 -B`, senza scrivere nella release, ed è riuscita prima
dell'attivazione.

## Lettura reale del registry

Una sottoscrizione MQTT locale read-only su `.20`, senza pubblicazioni, ha
ricevuto immediatamente i quattro messaggi retained:

| Record | Valore osservato |
|---|---|
| stato attuale | `COOLING_ON` |
| motivo logica | `MANTENIMENTO_COOLING_ON` |
| temperatura impostata | `23` |
| modalità aria | `Raffrescamento` |

Questa è una fotografia della prova, non uno stato corrente permanente e non
una conferma fisica del compressore.

## Collegamento TLS, Groq e risposta

La richiesta autenticata a
`https://domopi.tailf30ba8.ts.net/v1/assistant/query` con la domanda «Perché il
condizionatore è acceso?» ha restituito HTTP 200. Groq ha selezionato un'unica
operazione:

```json
{"id":"op1","tool":"current_air_conditioner"}
```

La risposta deterministica ha riportato i quattro valori, il timestamp di
ricezione, retained e i limiti su freschezza, causalità e conferma fisica. Lo
stato complessivo era `partial`, visualizzato dall'app come «Dati con limiti»,
perché una lettura corrente con età sorgente ignota non viene dichiarata
copertura completa.

Il backend ha restituito il contesto chiuso `climate/air_conditioner`. Una
seconda richiesta autenticata con la sola domanda «Perché?» e quel contesto ha
prodotto lo stesso piano e la stessa risposta. La chiave Groq e il token Android
sono stati letti dai file privati già installati e non sono comparsi nell'output
né nel repository.

## Copertura e limiti

La prova certifica il percorso MQTT `.20` → snapshot Android equivalente → TLS
Tailscale → backend → gateway Groq → tool validato → risposta. La sottoscrizione
MQTT e la chiamata TLS sono state eseguite da `.20`; la raccolta automatica dei
topic nell'APK è coperta da test e compilazione. APK debug e APK dei test sono
stati costruiti; i cinque test strumentali `HouseAiRepositoryTest` sono passati
sull'emulatore Android 14, compreso snapshot clima e contesto. In questa verifica
non è stato premuto manualmente il pulsante «Chiedi» né provata la voce.

Il motivo `MANTENIMENTO_COOLING_ON` è un codice pubblicato dal controller. Questo
incremento lo espone fedelmente; una futura spiegazione più ricca potrà
correlare soglie, sensori e log soltanto attraverso ulteriori tool validati e
con provenienza esplicita.

## Rollback

La release precedente resta
`/opt/domopi-house-ai/releases/20260913-stack-health`. Per tornare indietro:

```sh
sudo ln -sfn /opt/domopi-house-ai/releases/20260913-stack-health /opt/domopi-house-ai/current.next
sudo mv -Tf /opt/domopi-house-ai/current.next /opt/domopi-house-ai/current
sudo systemctl restart house-ai-gateway house-ai-backend
```

Nessuna modifica è stata effettuata a Node-RED, Mosquitto, `.15`, Tailscale
Serve, credenziali o dispositivi.
