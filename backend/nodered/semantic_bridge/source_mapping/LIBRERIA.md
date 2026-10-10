# Mappa delle origini — libreria

> Aggiornamento: la [catalogazione dashboard_only_v2](../actor_policy/README.md)
> sostituisce le precedenti classificazioni: dashboard utente, Alexa e tutte le
> altre sorgenti automazione. Le descrizioni precedenti restano storiche dove divergono.


Analisi in sola lettura del 22 settembre 2026. Punto 2 del piano: mappatura,
nessuna implementazione o deploy. Perimetro pilota: lights.libreria.power.
Il file progetto .20 è stato confrontato con GET /flows autenticato: coincidenza
completa, revisione `5c2d77d1f09dd553267de300e9ee10fa886e71a610c47cb370b080f6d0611de0`.
Le evidenze sono di configurazione: non attestano che ogni percorso sia stato
esercitato durante l'analisi.

## Destinazione comune

ON: link in `449db0e6.11864` → RM `f5f14a57.6f51a8` → report
`f7bc8ba8.270b6`. OFF: link in `b2f9c1a1.fa88c` → RM `375cd5f5.f38faa`
→ report `18386049.c296a8`. Entrambi i report pubblicano tramite
`a9d973a9.ca666` su `zara/domotics/lights/shelflamp`.
Esistono inoltre ingressi RM attraverso ritardi delle scene e dello scheduler.
Non limitare l'inventario ai link in; alcuni nomi link e liste links inverse
sono fuorvianti. Ricostruzione effettuata seguendo wires e link out → link in.

## Origini e classificazione proposta

| Origine | Evidenza nel flow | Autore da attribuire | Punto per la futura marcatura |
|---|---|---|---|
| Dashboard Android / Smart, comando individuale | Router `5a7c1d360710ca21`, topic `zara/interface/lights/libreria/power/cmd` → function 17 | utente, convenzione del canale app | Già implementato; app dashboard e Smart non distinguibili dal solo topic |
| Dashboard Node-RED, switch libreria | `ad32e56b.84cfe` → `c14c803c.86e27` → `47f620dc.6ac2c` → switch `9d7004c9.a67e28` | utente | Uscita UI prima della confluenza; passthru=false |
| Alexa, dispositivo “luci libreria” | `2e66d0dd.ac93d` → switch `9d7004c9.a67e28` | utente | Uscita dispositivo Alexa prima dello switch condiviso |
| Scheduler | `41bab9be.81c0e8` → `daffc993.72e418` | automazione per gli eventi schedulati | Identificare anche la causa d'ingresso dello scheduler, vedi limite sotto |
| Stato TV LG | MQTT `7dffb6bd4d8ed545`, topic `LGtv/power/status` → RBE `17dda6952d320849` → switch `47b4e6deb69b994a` → link `5bd87c6ad192e8a0` | automazione, origine lg_tv_rule | Dopo il ramo decisionale che emette OFF libreria; non attribuire l'autore che ha acceso la TV |
| Scene app | Router → Scene Dispatcher `937d52b7b946ba3b` → TV/sleep/all_on/all_off | utente | Prima che Scene Dispatcher ricrei i messaggi e perda i metadati |
| Scene dashboard Node-RED | pulsanti `71aeaed6.a4d5b8` (TV), `53b3ae4.c08ccd` (notte), `a52920f.8a0476` (all on), `e5c6a02.cf0d46` (all off) | utente per click UI | Alla sorgente, distinguendo eventuale passthrough |
| Scene Alexa | `5b048bd8.9c1794` (TV), `7b2e5e45.6a8be8` (notte), `7afa36cd.02d79` (luci) | utente | Uscite Alexa prima delle confluenze scene |
| MQTT legacy verso function 17 | sottoscrizione `zara/domotics/lights/+` | sconosciuto senza evidenza aggiuntiva | Non classificare per topic o assenza di comando app |

### Scheduler e condizioni

Configurato ON al tramonto e OFF alle 23:00, tutti i giorni, non sospeso.
ON attraversa controllo holiday (`0e6ba58bc3eeb54c`), controllo luciECO
(`18799e3de7481cb9`) e la catena di ritardi prima del RM libreria; non tutti gli
ON dello scheduler raggiungono necessariamente la libreria. OFF passa dalla
Function `56d546.98b642bc` e dal ritardo `2de717f2.b8e57`.
Questi controlli restano invariati: marcare la provenienza non autorizza a saltarli.

Lo scheduler riceve anche un inject manuale `1e518e5581be0f6d` con payload
send_state, once=false e nessuna periodicità. Prima di implementare l'attribuzione
va verificato nel nodo schedex come distinguere emissione pianificata da emissione
richiesta manualmente. Non etichettare indistintamente tutto il suo output come
azione automatica. L'inject OFF `da53ac3a.cbb718` è anch'esso manuale, non startup.

### Scene e ingressi manuali

All off converge in `acff3264.d487c`; all on in `a8512076.5af378`; TV in
`73a44c65.2cfd94`; notte in `3d92676c.84cb88`. Questi punti sono condivisi:
la provenienza deve arrivare con il messaggio originale.

Il pulsante all off ha passthru=true ed è alimentato dall'inject manuale
`739a7011.c5a33` (once=false, nessuna schedulazione). Non usare il semplice
passaggio in un nodo UI come prova di click. Gli inject manuali rappresentano
un operatore dell'editor e vanno distinti con origin=editor_manual, se inclusi.

Il ramo OFF LG scatta sul payload stringa "true"; il ramo "false" non è collegato.
È una decisione automatica sulla libreria anche quando la TV è stata accesa da
un essere umano. Un messaggio retained/di riallineamento può alimentare una
regola: la marcatura non deve inventare un nuovo gesto umano.

## Punti aperti prima della patch

- Verificare la semantica schedex per send_state e la conservazione dei metadati
  attraverso scene/ritardi. Gli attuali report RM accettano solo actor=utente:
  andranno estesi esplicitamente prima di aspettarsi riscontri automazione.
- Il nodo switch `3203c367.13e60c` ha uscita verso RM OFF ma nessun ingresso
  cablato individuato; la Function ON `c5fe6b1c.89db88` è anch'essa senza ingresso.
  Non sono contati come sorgenti operative dimostrate.
- L'export .15 del 14 settembre fornito dall'utente non contiene riferimenti
  testuali shelflamp/libreria. È una fotografia storica, non una certificazione
  del runtime .15 corrente né prova di assenza di publisher con topic dinamici.
- Non sono state enumerate identità dei client MQTT: il topic condiviso non
  identifica univocamente il publisher. I percorsi esterni restano sconosciuti.
- Le altre luci, inclusa lettura, richiedono una propria mappatura delle origini:
  la validazione RM ON/OFF non dimostra automaticamente la stessa topologia.

## Decisione per il punto 3

Aggiungere provenienza esplicita ai rami identificati prima delle confluenze,
propagare command_id senza alterare payload, filtri, tempi o comandi. Usare solo
utente/automazione quando l'evidenza lo permette, altrimenti null con motivo.
I riscontri continuano ad attestare send_invoked, non stato fisico.
Lavorare prima su comandi diretti UI/Alexa e regola LG; scheduler e scene richiedono
le verifiche sopra. Nessun nodo è stato modificato durante questa mappatura.
