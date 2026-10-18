# AI-CLIMATE-VMC-OVERRIDE.1 — priorità manuale della VMC con scadenza (fase T2)

Progetto: [`docs/AI_MODE/COMANDI_A_TEMPO.md`](../../../docs/AI_MODE/COMANDI_A_TEMPO.md).

La velocità della VMC la decide AI Climate (1–3 secondo umidità, limite notturno, antirimbalzo di
5 minuti) e la reinvia solo quando cambia il suo calcolo: una velocità messa a mano veniva
sovrascritta al primo cambio. Con questa patch:

- comando `zara/interface/ventilation/vmc/override/cmd` = `{"speed": 1..4, "until": "<ISO con fuso>"}`
  (al massimo 48 ore avanti) oppure `"auto"`;
- nuova funzione «VMC priorità manuale» ([`override.js`](override.js)) nel tab «AI climate»: invia
  subito la velocità (stesso comando dei pulsanti dell'app), salva la priorità nel contesto `file`,
  pubblica lo stato retained `zara/interface/ventilation/vmc/override/stat`, la chiude alla scadenza
  (controllo ogni 60 s) o con `"auto"`;
- funzione di AI Climate: un blocco prima dell'antirimbalzo — finché dura la priorità la velocità è
  quella manuale, anche sopra il limite notturno e l'anticondensa (decisione dell'utente), motivo
  `MANUALE_FINO_ALLE_HH:MM`; poi AI Climate riprende al ciclo successivo.
- Condizionatore, Predictive Reserve, uscite e fili invariati. Router invariato.

```sh
python3 build.py <flows-export.json> && TZ=Europe/Rome node --test test.js
python3 deploy.py prepare | apply BACKUP | rollback BACKUP
```
