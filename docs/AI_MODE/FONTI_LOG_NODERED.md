# Fonti log Node-RED — 12 settembre 2026

Verificati via SSH in sola lettura su 192.168.1.20: esistenza, dimensioni e
ultime due righe dei quattro file in `/home/pi/AI_climate`. Nessun servizio
installato, file remoto modificato o comando domotico eseguito.

| Source tool | File | Riferimento temporale osservato | Significato |
|---|---|---|---|
| learning | apprendimento_storico.log | timestamp, millisecondi | medie orarie e aggiornamenti EMA registrati |
| climate | clima_controllo.log | timestamp, millisecondi | eventi, ragioni e comandi registrati |
| reserve_shadow | predictive_reserve_shadow.log | timestamp_ms | simulazione con control_effect NONE_SHADOW_ONLY |
| solar_forecast | zara_previsione_kwh_openmeteo.log | data_previsione | giorno previsto, senza timestamp di elaborazione |

Il campione SHADOW più recente è del 6 settembre: non prova lo stato corrente.
Quello solare contiene data_previsione 2026-09-12 e kwh_stimati_impianto_reali 18:
è una previsione, non una misura di produzione. La verifica di due righe per
file non certifica uniformità dell'intero archivio.

## Integrazione

Il pianificatore può richiedere `backend_log_day` con `id`, `source` e `day`.
Il backend accetta esclusivamente i quattro identificatori della tabella;
nessun percorso proviene da Android o dal modello. Configurare
`HOUSE_AI_LOG_ROOT=/home/pi/AI_climate` quando il backend dispone di tale
cartella, oppure un percorso locale con copie ottenute separatamente.
Questa implementazione non effettua trasferimenti SSH automatici.

HouseAiRepository usa già POST /v1/assistant/query e riceve le evidenze in
results. La risposta testuale/vocale fornisce per ora conteggio e limiti;
non interpreta ancora semanticamente ogni campo dei log e non visualizza
un dettaglio dedicato dei record. Il modello configurato riceve il catalogo,
non il contenuto grezzo dei log. Restano necessari configurazione del gateway
modello e accesso del processo backend ai file per la prova integrata.

Lettura massima 32 MiB per sorgente, righe fino a 128 KiB, ultimi 50 record
corrispondenti entro 160 kB grezzi per operazione. File/riga, record originali,
righe invalide, indisponibilità e troncamento sono espliciti. Nessuna deduzione
di copertura continua, freschezza corrente o conferma fisica. Le evidenze
presenti restano partial; assenza di record non significa assenza di eventi.

Verifica locale: 41 test backend superati, inclusi mapping temporali,
provenienza, input non consentiti, troncamento e assistente. Nessuna prova
Android o deployment inclusa in questo incremento.

## Aggiornamento successivo del 12 settembre

Per lo stato finale dell'implementazione, le funzioni aggiunte, la configurazione
e i limiti ancora aperti consultare [SMART_ENERGIA.md](SMART_ENERGIA.md).
