# Guida utente — AI Smart (app Z-AI)

Aggiornata al **23/10/2026** · app `bld_d1ef3f89` · backend House AI `20261023-health`.

AI Smart è la modalità dell'app in cui parli con la casa: fai domande a voce o per iscritto
e ricevi risposte brevi, pensate per essere ascoltate. Può anche accendere e spegnere le luci,
avviare le scene luci, cambiare la temperatura dei termostati e impostare il caminetto
(modalità, orari, potenza automatica; acceso/spento solo in modalità Manuale e dopo la tua
conferma). Gli altri impianti li legge soltanto.

> I numeri delle risposte sono sempre quelli misurati dalla casa: li scrive il programma, non
> l'intelligenza artificiale. L'intelligenza artificiale capisce solo **cosa** stai chiedendo.

---

## 1. Come si attiva

1. Apri l'app **Z-AI**.
2. Nella barra in alto tocca **AI smart** (accanto al nome Z-AI).
3. Per tornare alla Home tocca **Indietro** oppure il tasto indietro del telefono.

**Prima volta / configurazione** (menu ⋮ in alto a destra → **Configurazione assistente**):

| Campo | Valore |
|---|---|
| Indirizzo backend AI | `https://domopi.tailf30ba8.ts.net` (già impostato) |
| Token backend | il codice di accesso del backend; viene salvato cifrato sul telefono |

**Dove funziona**
- **In casa** basta il Wi-Fi di casa.
- **Fuori casa** serve **Tailscale** attivo sul telefono.

---

## 2. La schermata

| Parte | A cosa serve |
|---|---|
| **Stato della domotica** | riepilogo scritto all'apertura: temperature ed energia del momento. Si può ridurre toccando la freccia. |
| **Leggi riepilogo** | legge a voce il riepilogo (all'apertura non parla da solo). |
| **Com'è andata oggi** | bilancio energetico della giornata, vedi §4.10. |
| **Parla** | tasto grande: tocca, fai la domanda, la domanda parte da sola e la risposta viene letta a voce. |
| **Oppure scrivi la domanda** + **Invia** | domanda scritta: la risposta compare ma **non** viene letta (c'è **Leggi risposta**). |
| **Risposta** | testo della risposta, ora della risposta, **Leggi risposta** / **Ferma lettura**. |
| **Provenienza e limiti** | da dove vengono i dati (topic, valori, flussi stimati) e i limiti della risposta. |
| Menu ⋮ | **Configurazione assistente**, **Diagnostica stack** (stato dei servizi), **Storico e motivazioni** (rapporto giornaliero e decisioni del clima). |

**Avvisi in alto (solo scritti, su ogni schermata):** «Casa non collegata.» / «Casa di nuovo
collegata.», «Batteria carica al 100 per cento.», «Batteria sotto il 20 per cento.».

Esempio di riepilogo all'apertura:
> Living 20,6 gradi, fuori 14,2. Il fotovoltaico produce 3,5 kilowatt, la batteria è al 48 per
> cento e si sta caricando, la casa consuma 516 watt, non stai importando dalla rete.

---

## 3. Come fare le domande

- Parla in modo naturale: «energia?», «com'è il living?», «accendi la lampada TV».
- Puoi unire più richieste in una frase (fino a 6 parti): «com'è il riscaldamento e quanto
  abbiamo prodotto ieri?».
- Per i periodi usa parole come *oggi, ieri, settimana scorsa, mese scorso, ultimi 7 giorni*
  o date precise. Sul periodo AI Smart **non chiede mai chiarimenti**: giorno, settimana, mese o anno senza
  numero («settimana», «ultima settimana», «mese», «quest'anno») = quello **in corso** (la settimana
  da lunedì); «ieri», «settimana scorsa», «mese scorso», «anno scorso» = quello precedente; «ultimi
  N giorni/settimane/mesi» = gli ultimi N, escluso oggi. Con il nome: «a settembre» (dell'anno in
  corso, o dell'anno scorso se non è ancora arrivato), «marzo 2025», «lunedì» (il più recente),
  «il 2025». «Percentuale di ricarica» = livello medio della batteria di casa.
- Se la domanda è ambigua, AI Smart chiede un chiarimento. **Rispondi solo al chiarimento** («ultimi
  7 giorni»), entro 2 minuti: se avevi parlato, il microfono si riapre da solo dopo la lettura. Se
  invece fai una domanda nuova e completa, risponde a quella. Per il resto non ricorda le domande
  precedenti, tranne «perché?» subito dopo una risposta sul condizionatore.
- Al massimo **circa 2 domande al minuto** (limite del servizio gratuito): oltre, compare
  «Troppe domande ravvicinate: riprova tra … secondi.».

---

## 4. Cosa puoi chiedere e cosa ti risponde

*I numeri negli esempi sono indicativi: la risposta usa i valori del momento.*

### 4.1 Energia adesso

| Domande | Risposta |
|---|---|
| «energia?», «stato fotovoltaico?», «come va il fotovoltaico?» | Il fotovoltaico produce 3,5 kilowatt, la batteria è al 48 per cento e si sta caricando, la casa consuma 516 watt, non stai importando dalla rete. |
| «dettaglio energia», «dettaglio fotovoltaico», «da dove arriva l'energia?» | la frase sopra, più i **soli flussi attivi**: Dal fotovoltaico: 516 watt alla casa e 3 kilowatt alla batteria. |
| una sola grandezza: «quanto consuma la casa?», «a quanto è la batteria?», «quanto produce il fotovoltaico?» | La casa consuma 516 watt. / La batteria è al 48 per cento. |

- La rete può dire «stai importando X dalla rete», «stai immettendo X in rete» oppure «non stai
  importando dalla rete». Di sera i flussi diventano, per esempio, «Dalla batteria: 900 watt
  alla casa. Dalla rete: 300 watt alla casa.».
- Sotto **50 watt** rete, batteria e flussi sono considerati fermi.
- I **flussi sono stimati** (nessun contatore li misura): il fotovoltaico va prima alla casa,
  poi alla batteria, poi in rete.
- Se un dato non arriva da più di 3 minuti, la risposta lo dice («Non aggiornati da più di 3
  minuti: rete.») e non lo usa.

### 4.2 Energia nel passato (da EmonCMS)

| Domande | Esempio di risposta |
|---|---|
| «quanto abbiamo prodotto ieri?», «consumo della settimana scorsa», «prelievo dalla rete a settembre» | Produzione fotovoltaica il 14/10: 18,2 kilowattora. |
| «confronta il consumo di questo mese con il mese scorso» | Consumo della casa …: X kilowattora in più rispetto al periodo …, +12 per cento. |
| «in quale giorno di settembre abbiamo prodotto di più?» | Il giorno con produzione fotovoltaica maggiore … è stato il …: X kilowattora. |

Grandezze disponibili: produzione fotovoltaica, consumo della casa, prelievo e immissione in
rete, energia caricata e scaricata dalla batteria, livello medio della batteria. Oggi e i
periodi in corso valgono fino ad adesso; i valori sono stime da campioni, non misure fiscali.
I kWh vengono dagli **stessi contatori dell'app EmonCMS MySolarBattery** (consumo casa,
solare e batteria dal Powerwall), quindi i numeri coincidono con quelli che vedi lì. Prelievo e
immissione vengono dal misuratore di rete del Powerwall. Il «rete → casa» dell'app è una stima per differenza, non un
contatore: per il prelievo vale quello di AI Smart.
La **copertura** (quanta parte del periodo ha dati in EmonCMS) è scritta nei dettagli sotto la
risposta, non letta; la voce dice solo «Mancano dati per una parte del periodo.» se ne manca più
del 5 % del tempo trascorso.

### 4.3 Temperature e stanze

| Domande | Esempio di risposta |
|---|---|
| «temperature in casa?», «come sono le temperature?» | Temperature: living 20,6, camera da letto 19,8, … gradi. |
| «com'è il living?», «living?» | Living: 20,6 gradi, umidità 55 per cento, humidex 22. Termostato: setpoint 18 gradi, non chiede calore. |
| «che temperatura c'è fuori?», «umidità in camera?» | Fuori ci sono 14,2 gradi. |

Stanze: living (soggiorno, sala), camera da letto, cameretta, bagno servizio, locale tecnico, esterno.

### 4.4 Riscaldamento (solo lettura)

| Domande | Esempio di risposta |
|---|---|
| «com'è il riscaldamento in casa?», «stato del riscaldamento?» | Caminetto spento. È in modalità Riscaldamento, programmato dalle 16:30 alle 16:55, con potenza automatica attiva. Puffer: alto 35,2 gradi, basso 33,1 gradi, acqua sanitaria 36 gradi. Pompa pavimento disabilitata e ferma. Caldaia a gas: fiamma spenta. Solare termico: pompa al 30 per cento, collettore 40,8 gradi. — più i due termostati. |
| «il caminetto è acceso?», «quanto è caldo il puffer?», «la caldaia è accesa?» | la stessa descrizione dell'impianto. |

Con il caminetto acceso: «Caminetto acceso a livello 3. …». Puffer, pompa pavimento, caldaia e
solare termico **non si comandano** da AI Smart; il caminetto si imposta come al §4.5.

### 4.5 Caminetto (comandi)

**Ogni comando del caminetto chiede conferma** (vedi §4.7): niente parte finché non dici
«confermo» o tocchi **Conferma**.

| Domande | Risposta |
|---|---|
| «metti il caminetto in modalità acqua sanitaria dalle 12:00 alle 13:00» | Metto il caminetto in modalità Acqua Sanitaria, dalle 12:00 alle 13:00. Confermi? |
| «metti il caminetto in riscaldamento» | Metto il caminetto in modalità Riscaldamento. Confermi? |
| «il caminetto si spenga alle 17:30» | Imposto gli orari del caminetto con spegnimento alle 17:30. Confermi? |
| «disattiva la potenza automatica del caminetto» | Disattivo la potenza automatica del caminetto. Confermi? |
| «accendi il caminetto» (in modalità Manuale) | Accendo il caminetto. Confermi? → dopo la conferma «Ok, accendo il caminetto.» |
| «spegni il caminetto» (in modalità Manuale) | Spengo il caminetto. Confermi? |
| «accendi il caminetto» (in un'altra modalità) | Il caminetto è in modalità Riscaldamento: per accenderlo a mano passa prima a Manuale. |
| «metti il caminetto in manuale e accendilo» | Metto il caminetto in modalità Manuale e lo accendo. Confermi? |
| «metti il caminetto a 4» (in Manuale, acceso) | Imposto il caminetto al livello 4. Confermi? |
| «metti il caminetto a 4» (in Manuale, spento) | Il caminetto è spento: il livello si cambia quando è acceso. |
| «metti il caminetto a 4» (in un'altra modalità) | Il caminetto è in modalità Riscaldamento: il livello si cambia solo in Manuale. Con la potenza automatica attiva il livello lo decide la casa. |

- **Modalità:** Riscaldamento, Acqua Sanitaria, Integrazione Caldaia, Manuale, Disattivato.
- **Orari:** a 24 ore; l'avvio deve essere prima dello spegnimento, altrimenti: «Non imposto
  gli orari del caminetto: l'avvio alle 17:00 deve essere prima dello spegnimento alle 16:55.».
  In Manuale e Disattivato gli orari non contano.
- **Acceso/spento** solo in modalità **Manuale**; se il caminetto è già nello stato chiesto:
  «Il caminetto risulta già acceso.» (nessuna conferma, non c'è nulla da inviare).
- **«Accendi il caminetto fino alle 19»:** acceso a mano, il caminetto **non si spegne da solo**
  all'orario (in Manuale gli orari non contano e la casa non spegne un'accensione manuale). AI Smart
  non cambia gli orari e lo dice prima della domanda di conferma: «Acceso a mano, il caminetto non si
  spegne da solo all'orario: dovrai spegnerlo tu, dall'app o dicendo «spegni il caminetto».
  Accendo il caminetto. Confermi?». Con i comandi a tempo (§4.14) lo spegnimento si programma.
- **Manuale e accensione insieme:** dopo la conferma l'app invia la modalità, aspetta 3 secondi
  che la casa la applichi, poi l'accensione.
- **Livello di potenza (1–6):** solo in modalità **Manuale** e a caminetto **acceso**. Nelle
  altre modalità non si cambia da AI Smart e vale la potenza automatica: attiva, il livello lo
  decide la casa; disattivata, resta quello impostato (slider della scheda Impianti). In Manuale
  la casa non cambia il livello da sola.

### 4.6 Termostati (lettura e comando)

| Domande | Esempio di risposta |
|---|---|
| «come sono i termostati?», «termostato del bagno?» | Termostato del living: 20,6 gradi, setpoint 18 gradi, non chiede calore. |
| «metti il living a 20 gradi», «imposta il bagno a 19,5» | Imposto il termostato del living a 20 gradi. Confermi? → dopo la conferma «Ok, imposto il termostato del living a 20 gradi.» |

- Valori ammessi: **da 16 a 22 gradi a passi di 0,5**; serve dire la stanza (living o bagno).
- «Chiede / non chiede calore» indica se il termostato della stanza sta chiedendo riscaldamento.
- Il timer delle fasce orarie e la modalità vacanza possono poi reimpostare il setpoint.
- Come il caminetto, **ogni comando dei termostati chiede conferma** (§4.7).

### 4.7 Conferma dei comandi del riscaldamento

Termostati e caminetto (e in futuro pompa pavimento e caldaia) **non partono senza conferma**;
luci e scene partono subito.

- Una **sola conferma per richiesta**, anche con più comandi: la domanda compare **subito sotto
  Parla** con i tasti **Conferma** / **Annulla**, senza bisogno di scorrere.
- In una richiesta mista («accendi la luce della libreria e metti il bagno a 20 gradi») la luce
  parte subito, il termostato aspetta la conferma; la domanda di conferma chiude la risposta.
- La domanda letta finisce con «…Confermi?» e **non contiene mai «confermo» o «conferma»**: così
  il microfono non può sentire il telefono confermare da solo.
- **Conferma a voce:** se il comando è stato detto a voce, **1 secondo** dopo la fine della
  lettura il microfono si apre da solo, con la finestra «Di' confermo o annulla»: parla solo
  quando la vedi. Rispondi solo **«confermo»** o **«conferma»** (una parola sola; «sì»
  o frasi più lunghe non valgono) oppure **«annulla»** / **«no»**. Se non rispondi o l'app non
  capisce: «Nessuna risposta: tocca Conferma o Annulla.» / «Ho sentito «…»: non è una conferma.
  Tocca Conferma o Annulla.» (con la frase capita), e non invia nulla. Accenti e spazi non
  contano («confermò», «con fermo») e vale anche un'alternativa del riconoscimento vocale. Con un
  comando scritto puoi toccare Parla e dire «confermo».
- Dopo la conferma l'app risponde con «Ok, …» e invia i comandi; se la casa non è collegata:
  «Comando non inviato: la casa non è collegata.».
- La conferma vale **60 secondi**: dopo, «Conferma scaduta: nessun comando inviato. Ripeti la
  richiesta.».

### 4.8 Luci (lettura e comando)

| Domande | Esempio di risposta |
|---|---|
| «come sono le luci?», «quali luci sono accese?» | Accese: lampada TV, prolunga. Spente: soggiorno, libreria, … |
| «la luce del portico è accesa?», «chi ha acceso la lampada TV?» | La lampada TV è accesa. Ultimo comando registrato: accensione dell'utente, 15/10 alle 09:12. |
| «accendi la lampada TV», «spegni le luci della piscina» | Ok, accendo la lampada TV. |

Luci comandabili: soggiorno, libreria, lampada TV, tavolino lettura, lavanderia, portico
ingresso, ingresso servizio, luci piscina, luci pedana piscina, prolunga.

### 4.9 Scene luci

| Domande | Cosa succede |
|---|---|
| «modalità TV» | accende lampada TV e soggiorno, spegne libreria e lampadario TV |
| «buonanotte», «modalità notte» | spegne tutte le luci dopo 2 minuti |
| «accendi tutte le luci» | accende le luci principali e della piscina (niente in modalità vacanza, meno luci con luci ECO) |
| «spegni tutte le luci» | spegne tutte le luci interne, esterne e della piscina (circa 35 secondi) |

### 4.10 Condizionatore e Predictive Reserve

| Domande | Esempio di risposta |
|---|---|
| «il condizionatore è acceso?», «perché il condizionatore è spento?» | Il condizionatore è spento. — con il motivo registrato dal controller AI Climate |
| «il Predictive Reserve è attivo?», «come funziona adesso il PR?» | Il Predictive Reserve è disattivato. / … attivo in modalità SHADOW: blocca gli avvii notturni in deumidificazione. |
| «perché stanotte il condizionatore non è partito?» | il motivo registrato quella notte nei log del clima; se quel giorno non ci sono stati cambi di stato, lo dice e riporta l'ultimo evento precedente (data, stato, motivo) |

Il condizionatore lo gestisce il controller AI Climate; da AI Smart lo si può **spegnere o accendere per
un tempo** (al massimo 6 ore), poi torna l'automatico: vedi §4.14.

### 4.11 «Com'è andata oggi» e log della casa

- Tasto **Com'è andata oggi** (non usa l'intelligenza artificiale):
  > Oggi fino alle 15:14: prodotti 14,7 kilowattora, consumati 6,4, prelevati 0,1, immessi 3,1.
  > Previsione per domani: 10 kilowattora.
- Domande sui log di Node-RED: «previsione fotovoltaica di domani?», «cosa ha registrato il
  controllo clima ieri?», «apprendimento orario di oggi?».

---

### 4.12 Regole «quando… allora…»

| Domande | Risposta |
|---|---|
| «quando il puffer alto arriva a 42 gradi spegni il caminetto» | Creo questa regola: quando il puffer alto supera 42 gradi spengo il caminetto, una volta. Confermi? |
| «se fuori scende sotto 5 gradi metti il bagno a 21» | Creo questa regola: quando la temperatura esterna scende sotto 5 gradi imposto il termostato del bagno a 21 gradi, una volta. Confermi? |
| «ogni sera alle 22 spegni tutte le luci» | Creo questa regola: alle 22:00 ogni giorno spengo tutte le luci, ogni volta. Confermi? |
| «che regole ci sono?» | Ci sono 2 regole: 1) … (in prova); 2) … (attiva). |
| «cancella la regola del caminetto» / «cancella tutte le regole» | Cancello la regola «…». Confermi? / Cancello 2 regole. Confermi? |

- Una regola ha **una condizione** (una misura che supera o scende sotto un valore, oppure un
  orario con i giorni) e **una azione** fra quelle che AI Smart sa già fare. Misure: puffer alto e
  basso, temperatura esterna, soggiorno, camera, cameretta, bagno di servizio, batteria,
  fotovoltaico, consumo della casa.
- Vale **una volta**, salvo «ogni volta» o «sempre». Se la condizione è già vera quando la crei,
  interviene subito.
- **Si crea solo dopo «confermo»** (o il tasto); poi resta **un giorno in prova**: annuncia cosa
  farebbe senza farlo, quindi si attiva da sola. All'intervento non chiede conferma.
- Al massimo **10 regole**. Le azioni sul caminetto seguono le stesse regole dei comandi
  (acceso/spento e livello solo in Manuale).
- Il motore delle regole sul `.20` è **attivo dal 18/10/2026**: finita la prova di un giorno, una
  regola agisce davvero (senza chiedere conferma) e lo notifica.
- **Notifiche**: ogni evento di una regola (creata, in prova «avrebbe agito», intervenuta, bloccata,
  cancellata) arriva come notifica nell'app **ntfy**, canale `domopi-regole`, in casa e fuori.

### 4.13 Profilo di riscaldamento e timer dei termostati (solo lettura)

| Domande | Risposta |
|---|---|
| «che profilo è attivo?», «perché è ridotto?» | Profilo di riscaldamento: Ridotto, automatico: la media esterna delle ultime 72 ore è 13 gradi, fra 10 e 16 gradi. Soglie: Pieno sotto 10 gradi, Spento sopra 16 gradi di media esterna. |
| «quando cambia il bagno?», «perché il soggiorno è a 17,5?» | Bagno: in fascia sera, 18 gradi; prossimo cambio oggi alle 22:00 a 17 gradi. |
| «com'è il timer dei termostati?» | profilo e piano di soggiorno e bagno insieme |

- Legge il profilo e il piano pubblicati dal timer sul `.20`. Se l'automatico ha meno di 48 ore di
  dati esterni lo dice («resta Ridotto finché non ha 48 ore»).
- Finché il timer è in prova aggiunge: «Il timer è in prova: per ora non cambia i termostati.»
- **Non cambia il profilo** a voce: si sceglie dalla scheda Clima dell'app.

### 4.14 Comandi a tempo («fino alle», «dalle», «dalle… alle…»)

| Domande | Risposta |
|---|---|
| «accendi il caminetto fino alle 19» (in Manuale) | Accendo il caminetto e alle 19:00 (oggi) spengo il caminetto. Confermi? |
| «accendi la luce del soggiorno fino alle 22» | Adesso accendo la luce del soggiorno e alle 22:00 (oggi) spengo la luce del soggiorno. Confermi? |
| «accendi la luce del portico dalle 18 alle 20» | Alle 18:00 (oggi) accendo la luce del portico e alle 20:00 (oggi) spengo la luce del portico. Confermi? |
| «spegni tutte le luci fino alle 7» (detto la sera) | Adesso spengo tutte le luci e alle 07:00 (domani) accendo tutte le luci. Confermi? |
| «metti la VMC a 3» | Metto la VMC a velocità 3 (AI Climate potrà cambiarla). Confermi? |
| «metti la VMC a velocità 3 fino alle 20» | Metto la VMC a velocità 3 fino alle 20:00, poi torna l'automatico. Confermi? |
| «VMC a 2 dalle 18 alle 20» / «VMC a 2 dalle 18» | Alle 18:00 (oggi) metto la VMC a velocità 2 fino alle 20:00, poi torna l'automatico. Confermi? (senza fine: 2 ore) |
| «rimetti la VMC in automatico» | Rimetto la VMC in automatico con AI Climate. Confermi? |
| «accendi il condizionatore in caldo a 20 fino alle 22» | Accendo il condizionatore in caldo a 20 gradi fino alle 22:00, poi torna l'automatico. Confermi? |
| «spegni il condizionatore fino alle 18» | Spengo il condizionatore fino alle 18:00, poi torna l'automatico. Confermi? |
| «metti il condizionatore in deumidifica dalle 18 alle 20» | Alle 18:00 (oggi) accendo il condizionatore in deumidifica a 24 gradi fino alle 20:00, poi torna l'automatico. Confermi? |
| «accendi il condizionatore» | In caldo, raffrescamento o deumidifica? |
| «rimetti il condizionatore in automatico» | Rimetto il condizionatore in automatico con AI Climate. Confermi? |

- **Luci, scene, caminetto, VMC e condizionatore**; la piscina più avanti. Mai l'irrigazione.
- **Condizionatore:** spento, oppure caldo (16–22 °C, se non dici la temperatura 20), raffrescamento
  (22–28 °C, 24) o deumidifica (23–25 °C, 24); **al massimo 6 ore**, senza ora di fine 2 ore. AI Climate
  lo applica e alla fine lo spegne e riprende lui. Rispetta i **15 minuti del compressore**: se è stato
  acceso o spento da poco, il comando parte allo scadere dei 15 minuti. D'estate, se la batteria
  scarica troppo o la riserva per la notte non basta, la protezione **chiude il comando** e arriva un
  avviso su ntfy. Se AI Climate è disattivato, la risposta lo dice: il comando non ha effetto.
- **VMC:** con un orario è una **priorità manuale** che AI Climate rispetta fino alla fine, anche se
  l'umidità chiederebbe di più (in quel caso la risposta lo dice); senza orario è una velocità secca
  che AI Climate può cambiare. I tasti 1–4 dell'app danno la priorità per 2 ore; «Auto» la toglie.
- **Una sola conferma** per tutta la richiesta. L'orario è il prossimo (oggi o domani), al massimo
  48 ore avanti. Se in «dalle… alle…» il primo orario è già passato, la prima parte avviene subito.
- Ogni orario futuro è una regola «una volta» **attiva subito** (senza giorno di prova): compare in
  «che regole ci sono?», si annulla con «cancella la regola del caminetto/delle luci», e arriva una
  notifica quando agisce.
- Caminetto: solo in **Manuale** (altrimenti: «Il caminetto non è in Manuale: i comandi a tempo
  valgono solo in Manuale…»). Se all'orario non è più in Manuale, il comando non parte e arriva la
  notifica «Regola bloccata».

### 4.15 Salute dell'impianto (solo lettura)

| Domande | Esempio di risposta |
|---|---|
| «come sta l'impianto?», «funziona tutto?» | L'impianto è a posto: .20 e .15 controllati 1 minuto fa, nessun guasto. |
| «ci sono guasti sul Raspberry 15?» | Sul .15 un guasto: Servizio emonhub, stato inactive. Cosa fare: … |
| «perché l'app non si collega?» | anche: «Il .20 non manda aggiornamenti da 12 minuti: il Raspberry o il suo controllo non funziona.» |

Legge i controlli che i due Raspberry fanno ogni 2 minuti (gli stessi del cuore in alto e della
pagina «Salute impianto», [manuale](MANUALE_SALUTE_IMPIANTO.md)); dice al massimo 3 guasti per
Raspberry, gli altri sono nei dettagli. Non ripara e non riavvia nulla. Serve l'app `bld_d1ef3f89` o
successiva collegata alla casa (è l'app che passa i controlli ad AI Smart).

## 5. Cosa AI Smart non fa

- Non comanda caldaia, pompa pavimento, piscina (pompa e skimmer),
  irrigazione, garage, Predictive Reserve; il livello del caminetto solo in Manuale.
- Non inventa numeri, costi o tariffe e non fa ipotesi sulle cause.
- Non ha memoria delle domande precedenti, salvo la risposta a un chiarimento (entro 2 minuti).
- **Non esegue subito una richiesta detta come regola**: «quando…», «appena…», «alle 22…»
  diventano sempre una regola da confermare (§4.12), mai un comando immediato.
- Non crea regole con due condizioni insieme o con azioni fuori elenco (irrigazione, piscina,
  garage, caldaia; il condizionatore solo con i comandi a tempo di §4.14).
- A una richiesta non disponibile risponde che l'azione non è disponibile o chiede un chiarimento.

---

## 6. Messaggi di errore

| Messaggio | Cosa fare |
|---|---|
| Assistente non trovato: fuori casa attiva Tailscale. | attiva Tailscale sul telefono |
| Il server di casa non risponde. | controlla Wi-Fi o Tailscale; riprova |
| Token backend non valido. | reinserisci il token in Configurazione assistente |
| Troppe domande ravvicinate: riprova tra … secondi. | aspetta qualche secondo |
| Il servizio di intelligenza artificiale non risponde. Riprova tra poco. | riprova più tardi |
| I dati di casa non sono raggiungibili in questo momento. | EmonCMS o i dati della casa non rispondono: riprova più tardi |
| La casa non è collegata: sono gli ultimi dati ricevuti. | la risposta usa gli ultimi valori ricevuti; controlla la connessione |

---

## 7. Aggiornamenti di questa guida

Questa guida va aggiornata **nello stesso commit** di ogni modifica alle funzioni di AI Smart
(domande, risposte, comandi, schermata, messaggi), aggiornando anche la riga «Aggiornata al»
in cima.

| Data | Modifica |
|---|---|
| 15/10/2026 | Prima versione: energia adesso e flussi, riscaldamento in sola lettura, modalità del caminetto in una frase propria. |
| 15/10/2026 | Comandi del caminetto (§4.5): modalità, orari e potenza automatica subito; acceso/spento solo in Manuale e con conferma; livello escluso. |
| 15/10/2026 | Tasti Conferma/Annulla del caminetto spostati sotto Parla; l'etichetta «Periodo coperto» compare solo nelle risposte su un periodo (consumi, produzione), non nei comandi. |
| 15/10/2026 | Livello di potenza del caminetto (1–6) da AI Smart, solo in Manuale e a caminetto acceso (§4.5). |
| 15/10/2026 | Conferma a voce di acceso/spento del caminetto («confermo»/«conferma»), ascolto automatico dopo la domanda, validità 60 secondi (§4.5). |
| 16/10/2026 | **Tutti i comandi del riscaldamento** (termostati, modalità, orari, potenza automatica, livello e acceso/spento del caminetto) chiedono conferma, una sola per richiesta; luci e scene restano immediate (§4.7). |
| 16/10/2026 | Conferma a voce più tollerante: accenti e spazi ignorati, alternative del riconoscimento vocale, frase capita mostrata se non è una conferma. |
| 16/10/2026 | Sicurezza: la domanda letta finisce con «Confermi?» senza «confermo»/«conferma» e il microfono si apre dopo 1 secondo (una prova aveva confermato da sola un termostato). |
| 16/10/2026 | Le richieste di regole automatiche («quando…», «appena…», «se… allora…», «automazione») non diventano mai un comando immediato: risposta che non sono ancora disponibili (§5). |
| 17/10/2026 | **Regole a voce** (§4.12): creare, elencare e cancellare regole «quando… allora…», con rilettura e conferma; un giorno in prova. Numerazione delle sezioni 4.8–4.11 corretta. |
| 17/10/2026 | **Notifiche delle regole** nell'app ntfy (canale `domopi-regole`, server sul `.20`). |
| 17/10/2026 | **Profilo di riscaldamento** (§4.13): AI Smart dice profilo, motivo e piano dei termostati (solo lettura). |
| 18/10/2026 | «Accendi il caminetto fino alle…»: in Manuale non cambia gli orari e avvisa che va spento a mano (§4.5). |
| 18/10/2026 | Motore delle regole **attivo** (§4.12); cancellate le due regole di prova. |
| 18/10/2026 | **Comandi a tempo** (§4.14) per luci, scene e caminetto: «fino alle», «dalle», «dalle… alle…», una sola conferma. |
| 18/10/2026 | **VMC** a voce (§4.14): velocità, «fino alle», «dalle… alle…», «rimetti in automatico»; priorità manuale rispettata da AI Climate. |
| 21/10/2026 | Servizio del modello al limite del minuto: «Troppe domande ravvicinate: riprova tra 60 secondi» invece dell'errore generico. Per ora circa una domanda al minuto. |
| 22/10/2026 | Domande più leggere: al modello vanno solo istruzioni e strumenti dell'argomento (energia, luci, clima, riscaldamento, regole, comandi a tempo), ~1300–4000 token invece di ~7700. Più domande al minuto e al giorno. «Prelievo» = dalla rete; «ultimo mese/settimana» chiede se calendario o ultimi giorni; «perché ieri notte il condizionatore…» legge il log di quel giorno. |
| 23/10/2026 | «Mese»/«ultimo mese» = mese corrente, senza chiarimento. Giorno senza eventi nel log del clima: «non ha registrato cambi di stato» e l'ultimo evento precedente. Risposta al chiarimento: basta dire la parte mancante; il microfono si riapre da solo (app `bld_059f6ff7`). |
| 23/10/2026 | Lettura a voce più naturale (app `bld_289903a4`): «21/10» si legge «21 ottobre» (non «ventuno decimi»), «17:00–21:00» «dalle 17:00 alle 21:00», «lun–ven» «lunedì a venerdì»; i trattini diventano pause e «—» «non disponibile». A schermo il testo non cambia. |
| 23/10/2026 | «Che profilo di riscaldamento è attivo?» funziona anche dal telefono (prima: «Schema evidenza non riconosciuto»; app `bld_a9748986`). «Ricarica» senza altro = batteria di casa; se i dubbi sono più di uno, AI Smart li chiede tutti insieme. |
| 23/10/2026 | Nessun chiarimento sul periodo: giorno, settimana, mese, anno senza nome = quello in corso; con il nome («settembre», «marzo 2025», «lunedì») quello indicato; «ultimi N…» = gli ultimi N. «Percentuale di ricarica» = livello medio della batteria. |
| 23/10/2026 | La copertura dei dati non viene più letta (resta nei dettagli); per i periodi in corso è calcolata fino ad adesso, non fino a fine giornata. A voce solo «Mancano dati per una parte del periodo.» se manca più del 5 %. |
| 23/10/2026 | Confronto con un periodo in corso («questa settimana rispetto alla scorsa»): a parità di tempo trascorso (la settimana scorsa fino allo stesso giorno e ora). kWh dai contatori dell'app EmonCMS MySolarBattery. |
| 23/10/2026 | Consumo casa dal contatore del Powerwall (feed 312), come ora l'app MySolarBattery: emontx3 HomeReading dal 21/10 fa salti (−34 % il 21, +81 % il 22). |
| 23/10/2026 | Prelievo e immissione (e «Importato oggi/ieri» nella home) dal misuratore di rete del Powerwall: la pinza emontx3 misura al contrario dal 19/10 (adattatore di tensione dell'emonTx staccato o cambiato), quindi prelievo e immissione erano scambiati. |
| 23/10/2026 | **Condizionatore a tempo** (§4.14): spento, caldo, raffrescamento o deumidifica fino a un'ora (al massimo 6 ore), poi torna AI Climate; conferma sempre; tempi del compressore rispettati; la protezione della batteria può chiudere il comando. |
| 23/10/2026 | **Salute dell'impianto** (§4.15): «come sta l'impianto?», «ci sono guasti sul .15?» con guasti e consigli dei controlli di .20 e .15. |
