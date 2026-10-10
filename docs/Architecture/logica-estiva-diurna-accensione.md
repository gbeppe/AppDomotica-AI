logica estiva diurna ACCENSIONE


                         AC SPENTO
                            │
                            ▼
                  Fascia DIURNA 08–22?
                            │
                           SÌ
                            │
                            ▼
                   Temperatura < 24 °C?
                      ┌─────┴─────┐
                     SÌ           NO
                      │            │
                      ▼            ▼
              BLOCCO ACCENSIONE   Timer anticiclo OFF
                                   terminato?
                                 ┌────┴────┐
                                NO        SÌ
                                 │         │
                                 ▼         ▼
                        BLOCCO TIMER     Controllo
                                         energia/comfort
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
                    ▼                        ▼                        ▼
          Periodo di grazia?          Batteria bassa?          Surplus insufficiente?
          + solo solare               SOC < soglia             surplusVirtuale <= 150 W
          + FV insufficiente          e Humidex < emergenza     e Humidex < emergenza
                    │                        │                        │
                   SÌ                       SÌ                       SÌ
                    │                        │                        │
                    ▼                        ▼                        ▼
             BLOCCO GRAZIA           BLOCCO BATTERIA          ATTESA SURPLUS
                    │                        │                        │
                    └────────────────────────┴────────────────────────┘
                                             │
                                             ▼
                                  Humidex < soglia attivazione?
                                      ┌──────┴──────┐
                                     SÌ            NO
                                      │             │
                                      ▼             ▼
                              COMFORT GIÀ OK     Possibile accensione
                              NON ACCENDERE          │
                                                   ▼
                                    transizionePermessa = true?
                                      ┌──────┴──────┐
                                     NO            SÌ
                                      │             │
                                      ▼             ▼
                               NON ACCENDERE    COOLING_ON
                                                comando IR
```

Più in dettaglio, la parte finale può essere vista così:

```text
                 CONDIZIONI BASE SUPERATE
                           │
                           ▼
                 temperatura >= 24 °C
                           │
                           ▼
             humidex >= sogliaAttivazioneLogica
                           │
                           ▼
                una delle seguenti abilita:
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
 SOC >= soglia      Forte solare        Humidex emergenza
 minima             disponibile         >= EMERGENCY_HUMIDEX_AWAY
        │                  │                  │
        └──────────────────┴──────────────────┘
                           │
                           ▼
                  transizionePermessa
                           │
                           ▼
                     COOLING_ON
```

La logica compatta è quindi:

```text
ACCENSIONE DIURNA CONSENTITA SE:

temp >= 24.0 °C
AND
humidex >= sogliaAttivazioneLogica
AND
timer anticiclo OFF terminato
AND
nessun blocco energetico attivo
AND
transizionePermessa = true
```

Dove `transizionePermessa` viene normalmente ottenuta con disponibilità energetica sufficiente:

```text
SOC >= socMinimoLogico
OR
solareForteDaOff
```

assieme a:

```text
surplusVirtuale > 150 W
```

oppure, come override comfort/emergenza:

```text
humidex >= EMERGENCY_HUMIDEX_AWAY
```

salvo il vincolo specifico del periodo di grazia in modalità `solo solare`.

La nuova regola `temp < 24 °C` è quindi un **blocco superiore e assoluto dell'accensione diurna**: anche se Humidex è alto, batteria piena e FV abbondante, sotto 24 °C il climatizzatore resta OFF. 




logica estiva diurna SPEGNIMENTO


                     AC ACCESO
                        │
        ┌───────────────┼─────────────────┐
        │               │                 │
     Comfort         Energia OK       Problema energia
     raggiunto       ma poco FV             │
        │               │                  │
        ▼               ▼                  ▼
   STANDBY 28°     timer deficit      SOC <= soglia?
                        │                  │
                        ▼              sì + no forte FV
                    STANDBY                 │
                                            ▼
                                           OFF