# WINTER-TEST.1 — prova invernale di AI Climate

Strumenti 3 e 4 di [`docs/AI_MODE/AI_CLIMATE_INVERNO.md`](../../../docs/AI_MODE/AI_CLIMATE_INVERNO.md), sezione 8. Richiede
[AI-CLIMATE-AC-OVERRIDE.2](../ai_ac_override2/README.md).

**Scheda «Prova invernale (DomoPi)»** ([`winter.js`](winter.js)), spenta finché non riceve il comando.

- Comando `zara/interface/climate/winter_test/cmd`: `{"action":"on"}`, `{"action":"off"}` o il collaudo
  `{"action":"run","until":"<ISO con fuso>"}` (al più 6 ore, anche a prova spenta). Stato e registro retained `…/winter_test/stat`.
- 08:30: FV previsto oggi (luce × 0,12 e 0,195) ≥ 12 kWh → avviso sul telefono con il tipo di giorno.
- 10:30: esterno (sensore Sonoff) fra 5 e 15 °C e caminetto spento, altrimenti giorno saltato, senza consumare l'alternanza.
  L'esterno si guarda alle 10:30 perché alle 8:30 è il minimo della giornata. I giorni adatti alternano **prova** e **confronto**.
- Prova: caldo 20 °C «solo sole» fino alle 15 e termostato del living 1 °C sotto il piano dei timer (minimo 16), riapplicato
  se un timer lo cambia. Alle 15, o se la priorità si chiude, il caminetto si accende o la prova viene spenta, il
  termostato torna al piano. Lo stato sta nel contesto `file`: dopo un riavvio la prova riprende o si chiude.

**Riepilogo** ([`report.py`](report.py), sul `.15`, sola lettura): giorno per giorno e verdetto con la soglia di 0,77.

```sh
python3 build.py <flows-export.json> && TZ=Europe/Rome node --test test.js     # 8 casi
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
