# Verifica reale gateway energia — 13 settembre 2026

Prova in sola lettura, senza installazione persistente e senza pubblicazioni MQTT.
La chiave Groq è rimasta nel file privato su `.20`; token e chiavi domestiche
erano effimeri o già presenti nell'app e non sono inclusi in questa evidenza.

## Risultati

- Python: 60 test superati su Python 3.9.25, inclusi errori fail-closed,
  calendario, file chiave privato e catena HTTP simulata.
- Android: build APK e APK test riuscita. `HouseAiLiveGatewayTest` eseguito su
  emulatore Android con rete inoltrata soltanto al backend temporaneo: 1/1 PASS,
  2,709 secondi. Ha verificato repository Android, risposta tipizzata e presenza
  di evidenze Digital Twin, EmonCMS feed 305 e log OpenMeteo.
- Sorgenti correnti reali su `.20`: cinque messaggi retained ricevuti dai topic
  pubblici; valori osservati nel campione: FV 5 W, casa 348 W, rete 0 W,
  batteria +360 W, SOC 71%. L'ora di ricezione non prova freschezza del sensore.
- EmonCMS `.15`: catalogo 256 feed. Confronto reale prelievo rete, 6 meno 5
  settembre: 0,0731438 kWh contro 0,1028222 kWh; differenza -0,0296784 kWh,
  copertura dichiarata 100% per entrambi. Restano stime da campioni, non fiscali.
- Quattro log reali del 6 settembre: apprendimento 24 record; clima 0; SHADOW
  83 record con risposta limitata/troncata; previsione 5. La risposta complessiva
  è correttamente `partial`; SHADOW resta simulazione.
- Richiesta composta corrente + storico + previsione: il piano 120B ha selezionato
  correttamente tre strumenti. La stessa richiesta, attraversata dal test Android,
  ha prodotto le tre famiglie di evidenze attese.

## Valutazione dei modelli

La suite contiene 16 casi sintetici scritti con l'implementazione. GPT-OSS 20B,
prima delle correzioni, 12/16. Difetti: omissione rete in domanda composta,
settimana errata e mancato chiarimento in due ambiguità. Dopo l'aggiunta del
calendario e di regole più precise, i quattro casi critici riprovati sono
migliorati, eccetto “ultimo mese”, che il 20B ha ancora interpretato male.

GPT-OSS 120B: 12/13 nei casi completati della ripetizione; unico errore osservato,
chiarimento superfluo per “Quanto sta producendo il fotovoltaico?”. Ha invece
risolto correttamente domanda composta, calendario, SOC ambiguo e “ultimo mese”.
La run completa è stata interrotta dopo avere raccolto evidenza sufficiente per
la scelta e per limitare consumo di quota. Per questo non si dichiara 15/16 o
16/16. Il modello predefinito è stato promosso a 120B, mantenendo validazione
deterministica e fallimento chiuso.

## Metodo e limiti

Su `.20` i sorgenti sono stati forniti a `python3 -B -` via SSH e copiati solo
in `TemporaryDirectory`; gateway e backend ascoltavano su loopback con token
casuali. Un tunnel SSH locale e `adb reverse` collegavano l'emulatore. Nessun
servizio, repository o pacchetto è stato installato su `.20`; `.15` è stato solo
interrogato via HTTP EmonCMS. Tutti i processi e tunnel temporanei sono stati
chiusi e le porte 8765/8766 non restano in ascolto.

Non coperti: TLS definitivo app→backend, telefono fisico, voce acustica,
prestazioni concorrenti, disponibilità prolungata Groq e suite indipendente.
Questa verifica non autorizza deployment o comandi ai dispositivi.
