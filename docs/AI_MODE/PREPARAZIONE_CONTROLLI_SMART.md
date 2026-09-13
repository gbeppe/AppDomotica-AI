# Preparazione controlli Smart Clima e Impianti

Stato del 13 settembre 2026: interfaccia e contratti predisposti in sola
lettura. Nessun comando viene inviato ai dispositivi.

## Decisione architetturale

La modalità Smart dovrà offrire tutte le regolazioni presenti nelle schede
dashboard Clima (`AiManagedScreen`) e Impianti (`HvacScreen`). L'attivazione dei
comandi viene separata dalla preparazione dell'interfaccia:

1. oggi Android riceve e valida soltanto i topic `/stat`;
2. mostra switch, stepper, slider, scelte e orari già organizzati come saranno
   usati in Smart, ma senza callback di comando;
3. conserva nel catalogo la coppia stato/comando verificata nel registry;
4. una fase futura aggiungerà un esecutore separato, conferma esplicita e
   verifica del nuovo `/stat` dopo il comando.

`SmartControlsScreen` non riceve `MqttManager` e non può chiamare `publish`.
`SmartControlState` non contiene metodi di comando. Il fatto che il catalogo
conosca un topic `/cmd` non lo rende eseguibile.

## Copertura preparata

Il catalogo contiene 23 controlli, derivati dai riferimenti effettivi delle due
schermate e confrontati con `device_catalog.json`.

### Clima — 9 controlli

| Gruppo | Controlli |
|---|---|
| Automazione | Predictive Reserve, abilitazione AI clima |
| Cicli compressore | minuti minimi ON, minuti minimi OFF |
| Comfort notturno | soglia Humidex, velocità massima VMC, tolleranza deficit |
| Gestione mattutina | abilitazione AC, soglia Humidex di emergenza |

### Impianti — 14 controlli

| Gruppo | Controlli |
|---|---|
| VMC | velocità 1–4 |
| Riscaldamento | abilitazione pompa pavimento |
| Termostato soggiorno | obiettivo, minima, massima |
| Termostato bagno | obiettivo, minima, massima |
| Termocamino | accensione, modalità, avvio, arresto, livello, automatico |

Ogni elemento usa il topic `/stat` corrispondente al `/cmd` già presente nella
dashboard. Valori booleani, numerici e orari invalidi vengono rifiutati. Una
modalità a scelta non riconosciuta viene conservata come stato leggibile, ma non
diventa automaticamente un'opzione futura inviabile.

## Interfaccia

Dalla home Smart il pulsante «Controlli Clima e Impianti · sola lettura» apre
la nuova schermata. Il banner superiore indica che l'invio è bloccato. Ogni
scheda mostra:

- valore ricevuto o «Dato non disponibile»;
- componente coerente con la dashboard, privo di azione;
- topic sorgente e flag retained quando esiste un'osservazione.

La schermata dashboard classica continua a funzionare secondo il comportamento
esistente. Questa modifica riguarda soltanto la modalità Smart.

## Verifica reale e limiti

Una sottoscrizione MQTT read-only su `.20` ai sei domini coinvolti non ha
ricevuto valori retained per i 23 topic nella finestra di quattro secondi
(`0/23`). Questo non prova che i dispositivi siano spenti o che i record non
vengano mai pubblicati: significa soltanto che non era disponibile una fotografia
retained durante la verifica. Smart lascia quindi i campi sconosciuti finché
non riceve un messaggio valido.

I test unitari verificano numero e unicità dei 23 contratti, separazione
`/stat`–`/cmd`, tipi, provenienza, ordine temporale e assenza di default
inventati. Il test Compose su emulatore Android 14 verifica valore corrente,
selettore e assenza di azione di click sullo switch.

## Requisiti prima di abilitare i comandi

La futura fase operativa dovrà introdurre un solo esecutore allowlist, esterno
alla UI, con:

- payload costruiti dal tipo del catalogo e mai dal testo libero del modello;
- riepilogo dell'azione e conferma esplicita dell'utente;
- controllo di connessione e autorizzazione;
- pubblicazione sul solo topic `/cmd` associato;
- attesa del `/stat` coerente, timeout e risultato distinto fra accettato,
  confermato e non verificato;
- audit sanitizzato senza credenziali;
- blocco globale che permetta di tornare immediatamente alla sola lettura.

Il pianificatore linguistico potrà proporre un'intenzione strutturata, ma non
avrà accesso diretto a MQTT e non potrà inventare topic o payload.
