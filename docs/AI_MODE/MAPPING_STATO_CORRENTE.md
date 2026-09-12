# Mapping dello stato corrente — 11 settembre 2026

Branch `feature/ai-home-assistant`. Catalogo iniziale per luci, living e ACS;
non è un inventario completo degli impianti domestici.

## Evidenze

Router esportato: `flows - 2026-09-11T113811.470.json`, funzione alle righe
157–171 (luci/piscina), 183 (ACS), 225 (living), 569–584 (pubblicazione retained).
SHA256 `0ddc5a4b57391378a14afa0fa7cffd1d000eea8b7de9b5650d10b7d41ddebbf2`.
Riscontro Android: `MqttManager.kt`, `LightsScreen.kt`.

Osservazione MQTT di 40 secondi sul broker configurato nell'app, mediante client
con identificativo temporaneo, solo subscribe ai topic pubblici di stato e nessun
publish. Messaggi ricevuti fra le 21:09:55 e le 21:10:20 Europe/Rome. Client
scollegato al termine. Le letture sotto sono una fotografia di quella prova.

## Temperature

| Misura | Topic pubblico | Provenienza nel Router | Osservazione |
|---|---|---|---|
| Living | `zara/interface/env/living/temperature/stat` | `emon/emonth5/temperature_calibrated` | 24,2 °C, un messaggio retained |
| Acqua sanitaria ACS | `zara/interface/energy/puffer_acs/stat` | `emon/shellyACS/tempACS` | Ultimo valore 41,1 °C, tre messaggi di cui uno retained |

ACS è la misura dedicata, non la temperatura superiore/inferiore del puffer.
Le sorgenti legacy sono qui riportate come evidenza: Android continua a usare
esclusivamente i topic pubblici. Nessun feed storico viene inventato o sostituito.

## Punti luce identificati

Prefisso comune: `zara/interface/`. Una riga corrisponde a un'entità logica;
una lampada con più alias non viene contata più volte.

| Entità | Topic relativo | ID Android | Stato ricevuto |
|---|---|---|---|
| Soggiorno | `lights/living/power/stat` | `sala` | true |
| Libreria | `lights/libreria/power/stat` | `libreria` | true |
| Lampada TV | `lights/tv/power/stat` | `televisione` | true |
| Tavolino lettura | `lights/reading/power/stat` | `tavolinolettura` | true |
| Camera | `lights/bedroom/power/stat` | `lucecamera` | false |
| Lampada HiFi | `lights/hifi/power/stat` | `lampadahifi` | non ricevuto |
| Luci piscina | `pool/water/power/stat` | `lucipiscina` | true |
| Luci pedana piscina | `pool/deck/power/stat` | `lucipedanapiscina` | true |

Risultato: **sei ON dichiarati, uno OFF dichiarato, uno sconosciuto su otto
entità mappate**. Non equivale a «sei luci fisicamente accese in tutta la casa».
Tutti gli stati luce ricevuti nella finestra erano retained. Lampada TV è
identificata dal mapping `tvlamp`, non va interpretata come televisore.

Esclusioni e lacune:

- Pompa e skimmer piscina: dispositivi non luminosi, esclusi dal conteggio.
- `lights/prolunga/power/stat`: ricevuto true, classificazione in attesa della
  risposta dell'utente sul dispositivo «Prolunga / Allarme». Escluso dal conteggio
  provvisorio; questo non significa che sia spento.
- Lavanderia, portico, cucina, esterno: presenti nella schermata Android ma privi
  di mapping pubblico nel Router esportato e non osservati nella finestra. Non
  inventare topic o includerli fra gli OFF.

## Freschezza e natura dello stato

Il Router emette valori scalari con retain, senza timestamp della misura.
Conservare distintamente ora di ricezione, flag retained e timestamp sorgente
(non disponibile). Né un retained appena ricevuto né un messaggio non retained
certificano da soli l'età della misura originale. Non usare il timestamp del
clima per datare sensori diversi. Soglie di scadenza e frequenze attese restano
da stabilire per sorgente; per ora `freshness` è `unknown`.

Molti mapping luci leggono lo stesso topic legacy usato per il comando:
lo stato può rappresentare un comando dichiarato, senza feedback fisico.
Il mapping HiFi usa un topic Tasmota di stato distinto, ma non ha fornito messaggi
nella finestra. Nessun comando di accensione è stato inviato per forzare risposte.

Risposta ammissibile del futuro assistente: «Gli ultimi stati disponibili
segnalano sei punti luce accesi fra quelli mappati. Per la lampada HiFi non ho
uno stato e non posso confermare l'aggiornamento degli altri.» Per living e ACS,
riportare i valori come ultime letture disponibili con età della misura ignota.

## Base implementata e verifiche

`backend/house_ai/current_catalog.json`: catalogo esplicito, provvisorio, con
esclusioni e copertura non completa. I dieci topic sono stati verificati contro
l'esportazione Router; nove hanno prodotto messaggi nella finestra (manca HiFi).

`current_state.py`: interprete separato, senza client MQTT o comandi, che riceve
osservazioni e produce uno snapshot. Mantiene mancanti/invalidi separati da zero
e OFF, ignora topic non mappati e `/cmd`, non duplica i conteggi alla ricezione di
più messaggi e non dichiara la conferma fisica. Gli schemi `house_ai.current_*`
sono nuovi contratti del prototipo, non campi già presenti nelle sorgenti.

Dieci test backend passati, di cui quattro nuovi su questi comportamenti.
Replay dei messaggi reali riuscito: sei ON, uno OFF, uno sconosciuto.
Nessuna modifica al runtime Android/MQTT, ai flow o ai Raspberry. Snapshot non
ancora esposto tramite API o scheda; riconoscimento e sintesi vocale da realizzare.

Prossimo passo: collegare questo modello a un adattatore di sola lettura e alla
scheda, mostrando subito provenienza e limiti; poi gestire le due domande di
esempio e la loro resa vocale. La classificazione dei punti mancanti e la
freschezza verificabile restano attività esplicite, non presupposti impliciti.

Il mapping è ora esteso a tutte le schede nel [catalogo dispositivi e capacità](CATALOGO_DISPOSITIVI.md), con distinzioni fra letture, comandi, risorse non MQTT e voci obsolete.

## Avanzamento del 12 settembre 2026

Completamento della scheda AI smart sul sottoinsieme luci/living/ACS, provenienza delle letture e interazione vocale: vedere [implementazione, verifiche e limiti](AVANZAMENTO_STATO_VOCE.md). Le osservazioni MQTT riportate sopra restano la fotografia dell’11 settembre, non letture correnti.
