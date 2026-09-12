# Domande complesse sui flussi energetici

Requisito utente del 12 settembre 2026. La modalità AI smart deve rispondere
a domande storiche in italiano, scritte e vocali, sui flussi energia della casa.
Il sottoinsieme attuale luci/living/ACS è una prima base e non esaurisce il prodotto.
Queste capacità sono richieste, non ancora implementate nella scheda smart.

## Primi casi di accettazione

- «Quanti kWh ho prelevato dalla rete nell'ultimo mese?»
- «Qual è stata la percentuale media di ricarica della batteria Tesla
  nell'ultima settimana?»
- Produzione fotovoltaica, consumo casa, energia immessa in rete, energia
  caricata/scaricata dalla batteria, confronti tra periodi e domande composte.

## Significati e periodi

Il prelievo è energia importata in kWh: non potenza istantanea e non saldo
netto fra importazione ed esportazione. Verificare unità e convenzione di segno
del feed prima di separare i flussi e integrarli; preferire un contatore di
energia validato se disponibile, gestendo reset e confini del periodo.

«Percentuale media di ricarica» è ambigua: distinguere livello medio di carica
(SOC, media ponderata nel tempo), incremento di carica durante le ricariche
e quota di energia caricata da una sorgente. La risposta deve chiarire
l'interpretazione; se non risolvibile dal contesto, chiedere quale misura serve.
Non convertire automaticamente variazioni SOC in kWh o in quota solare.

Distinguere mese/settimana di calendario precedenti da ultimi 30/7 giorni.
Mostrare sempre inizio/fine effettivi in Europe/Rome, gestire cambio d'ora e
periodi incompleti; chiarire il periodo quando la frase è ambigua.

## Base disponibile nel repository

Il catalogo e `EnergyRepository.kt` associano EmonCMS a solar 307, consumption
303, grid 305, battery 306, soc 304. Il rapporto backend corrente usa AC 353,
SOC 304 e batteria 306. Questi sono mapping statici, non una nuova verifica
live di disponibilità, unità, segni o copertura mensile.

## Implementazione prevista e verifiche

1. Validare metadati e campioni delle serie reali in sola lettura.
2. Tradurre la domanda in metrica, periodo e aggregazione espliciti.
3. Calcolare nel backend con metodi deterministici; l'eventuale modello
   interpreta e formula la risposta usando i risultati verificati.
4. Gestire richieste mensili senza perdere i flussi opposti per effetto
   di medie troppo grossolane; controllare risoluzione e limiti delle richieste.
5. Restituire valore, unità, periodo, fonti, copertura, esclusioni e limiti.
   Buchi non trasformati in zero; dati parziali non presentati come totali.
6. Collegare lo stesso risultato a testo e lettura vocale nella scheda AI smart.

Test richiesti: import/export alternati, campioni irregolari, buchi, reset
contatore, SOC invalido, medie temporali, cambio d'ora, periodi relativi e
domande ambigue/composte. Confrontare risultati con serie EmonCMS verificabili.
Nessun comando ai dispositivi o deployment incluso in questo requisito.

## Stato del primo incremento

Mapping rete e SOC verificato su EmonCMS reale; calcolo, catalogo sorgenti,
interprete italiano ed endpoint autenticato implementati nel backend. Vedere
[validazione dello storico energia](VALIDAZIONE_STORICO_ENERGIA.md). Restano da
collegare la scheda Android e la voce; le altre metriche energetiche sono ancora
requisiti successivi.
