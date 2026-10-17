# Guida utente — AI Smart (app Z-AI)

Aggiornata al **17/10/2026** · app `bld_bff71307` · backend House AI `20261017-heating-profile2`.

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
  o date precise. Se la domanda è ambigua, AI Smart chiede un chiarimento: riformula la domanda
  (non ricorda le domande precedenti, tranne «perché?» subito dopo una risposta sul condizionatore).
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
periodi in corso sono parziali; i valori sono stime da campioni, non misure fiscali.

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

### 4.10 Condizionatore e Predictive Reserve (solo lettura)

| Domande | Esempio di risposta |
|---|---|
| «il condizionatore è acceso?», «perché il condizionatore è spento?» | Il condizionatore è spento. — con il motivo registrato dal controller AI Climate |
| «il Predictive Reserve è attivo?», «come funziona adesso il PR?» | Il Predictive Reserve è disattivato. / … attivo in modalità SHADOW: blocca gli avvii notturni in deumidificazione. |
| «perché stanotte il condizionatore non è partito?» | il motivo registrato quella notte nei log del clima |

Il condizionatore **non si comanda** da AI Smart: lo gestisce solo il controller AI Climate.

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
- Il motore delle regole sul `.20` è **anch'esso in prova** finché non lo attiviamo: per ora le
  regole annunciano soltanto.
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

## 5. Cosa AI Smart non fa

- Non comanda caldaia, pompa pavimento, condizionatore, VMC, piscina (pompa e skimmer),
  irrigazione, garage, Predictive Reserve; il livello del caminetto solo in Manuale.
- Non inventa numeri, costi o tariffe e non fa ipotesi sulle cause.
- Non ha memoria delle domande precedenti.
- **Non esegue subito una richiesta detta come regola**: «quando…», «appena…», «alle 22…»
  diventano sempre una regola da confermare (§4.12), mai un comando immediato.
- Non crea regole con due condizioni insieme o con azioni fuori elenco (irrigazione, piscina,
  garage, condizionatore, caldaia).
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
