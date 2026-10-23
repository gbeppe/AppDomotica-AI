# Validazione dello storico energia — 12 settembre 2026

> **Aggiornamento 23/10/2026 — contatori cumulativi.** Dal backend `20261023-counters` i kWh dei
> periodi vengono dai contatori cumulativi dell'app EmonCMS MySolarBattery (ultima − prima lettura):
> casa 37 `emontx3/HomeReading`, solare 314 `solar_instant_power_KWh`, carica 310 e scarica 311
> `battery_*_power_KWh`, prelievo 26 `emontx3/ImportReading` e immissione 29 `emontx3/ExportReading`;
> SOC resta 304. Il feed 313 `site_instant_power_KWh` (`import_kwh` dell'app) è il **saldo netto** con
> la rete, non il prelievo. Verifica sui 7 giorni al 23/10: casa 79,0 kWh (app 79,7; Tesla 303 solo
> 67,6, −12 %), solare 107,8 (108,0), scarica 45,1 (45,1), prelievo 3,8, immissione 33,3. Il mapping
> qui sotto è quello del 12/09, superato per i kWh.

Verifica in sola lettura su EmonCMS locale, senza modificare feed, flow, MQTT o
dispositivi. Il catalogo ha restituito 256 feed. Le credenziali non sono state
copiate nei documenti o nei nuovi file del backend.

## Mapping verificato

| Metrica | Feed | Serie | Intervallo | Semantica verificata |
|---|---:|---|---:|---|
| Prelievo/immissione rete | 305 | `TeslaPowerwall/site_instant_power` | 30 s | positivo = prelievo; negativo = immissione; W |
| Livello batteria | 304 | `TeslaPowerwall/SOE` | 30 s | SOC in percentuale, intervallo valido 0–100 |

I metadati EmonCMS hanno il campo unità vuoto. L'unità del feed 305 e il suo
segno sono stati verificati incrociando feed, mapping Android e bilancio
`carico - fotovoltaico - batteria - rete`: il 6 settembre lo scarto assoluto
mediano è 7,25 W e il 95° percentile 22,75 W. Nella stessa giornata il calcolo
sul lato positivo del feed 305 produce 0,07314 kWh; sul lato negativo 19,02642
kWh. Le serie legacy separate mostrano rispettivamente 0,03406 kWh e 20,69207
kWh con copertura 98,02%. La differenza sconsiglia di trattarle come riferimento
identico; il backend usa il feed Tesla 305 già adottato dall'app.

## Prove sui periodi richiesti

- Mese precedente, 1 agosto–1 settembre 2026: prelievo stimato **2,90308 kWh**,
  copertura temporale **97,8304%**. Stato `partial`: non è presentabile come
  totale mensile completo.
- Settimana precedente, 31 agosto–7 settembre 2026: SOC medio temporale
  **68,9687%**, copertura **99,5040%**. Stato `partial`.

Un secondo calcolo indipendente sugli stessi campioni coincide entro
`2,3e-14 kWh` per il prelievo ed esattamente per il SOC. I file grezzi usati
per il controllo restano in `.validation/energy-live/`, ignorati da Git; i loro
SHA-256 sono registrati nei rapporti locali. I valori sono una fotografia della
verifica, non dati aggiornati in tempo reale e non misure fiscali del contatore.
La perdita dei file grezzi non impedisce di proseguire: mapping, metodo, risultati,
limiti e test di calcolo sono tutti versionati nel repository.

## Limiti e conseguenze

I buchi non diventano zero e il risultato non viene estrapolato. La copertura
misura il tempo integrabile, non garantisce accuratezza fisica. Il SOC medio è
una media temporale del livello, non energia caricata, quota solare o rendimento.
Nel periodo settimanale sono presenti otto variazioni adiacenti oltre tre punti
percentuali: non vengono eliminate automaticamente perché potrebbero essere
reali; richiedono un criterio separato basato su evidenze.

Il mapping promosso è in `backend/house_ai/energy_sources.json`. Il nuovo endpoint
protetto `/v1/energy/query` espone le prime domande deterministiche in italiano.
La scheda Android AI smart non usa ancora questo endpoint.
