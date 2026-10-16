# Pompa del pavimento (ESP8266, OTA `floorPump`)

Relè della pompa del riscaldamento a pavimento, comandato dalla logica della pompa sul Node-RED
del `.15` (tab «Sheet 1»: con la pompa abilitata, accesa con puffer alto > 35 °C, spenta sotto
30 °C). Broker `.15`, base `/esp8266/floorPump`. Il 16/10/2026 pompa ed ESP sono senza
alimentazione (pompa ferma dal 5 marzo 2026).

| Cartella | Cos'è |
|---|---|
| [`floorpump_v2/`](floorpump_v2/) | versione nuova, **caricata il 16/10/2026** (IP `192.168.1.21`, rete `TP-Link_SalaTecnica` del TL-WA850RE `192.168.1.236` in modalità access point) |
| [`pompapavimento_switch/`](pompapavimento_switch/) | versione in uso (copia di `~/Desktop/dev/pompapavimento_switch`, dicembre 2025), solo credenziali spostate in `secrets.h`; per tornare indietro |

## Cosa cambia in `floorpump_v2`

- Stessi topic e comandi (`/switch1/enable` `1`/`0`, `/switch1/status` `close`/`open`), così il
  `.15` non cambia.
- **Spegnimento di sicurezza** (scelta dell'utente): senza comandi sul relè per 15 minuti la
  pompa si spegne. Con il puffer caldo caminetto e caldaia modulano da soli; una pompa accesa
  senza controllo consumerebbe gas e pellet.
- `/online` con last will, `/switch1/status` retained, `/hello` JSON (Wi-Fi, stato, ultimo
  comando, spegnimenti di sicurezza, uptime) ogni 15 minuti e a ogni cambio.
- Riconnessione Wi-Fi/MQTT senza blocchi (l'OTA risponde anche senza broker), riavvio se il Wi-Fi
  manca da 10 minuti; relè aperto all'avvio e durante l'OTA; niente attesa fissa di 9 s.
- Credenziali in `secrets.h`; password OTA facoltativa (`OTA_PASSWORD`).

**Prerequisito sul `.15`** (FLOORPUMP-REPEAT.1, applicata il 16/10/2026): ripetere l'ultimo comando anche nella fascia 30–35 °C; prima
in quella fascia non invia nulla e lo spegnimento di sicurezza fermerebbe la pompa dopo 15 minuti.

## Caricare o tornare indietro

1. Alimentare la scheda; deve comparire tra le porte di rete dell'IDE come `floorPump`.
2. Arduino IDE: **Generic ESP8266 Module**, Flash Size **1MB (FS:64KB OTA:~470KB)**, Flash Mode
   **DOUT** (da confermare sulla scheda: il binario del 2017 era «generic»); Upload.
   Peso: ~295 KB di codice, entro i ~470 KB disponibili per l'OTA. Impostazioni confermate dal caricamento del 16/10/2026.
   **Attenzione alla porta**: l'IDE ricorda l'ultima usata, controllare che sia `floorPump at 192.168.1.21` e non il caminetto (`.22`).
3. Verifica: `/online online` e `/hello` con `"Firmware":"floorpump_v2"`.
4. Per tornare indietro: caricare allo stesso modo `pompapavimento_switch`.

Compilazione di verifica: FQBN `esp8266:esp8266:generic:eesz=1M64,FlashMode=dout,ResetMethod=nodemcu`
(core esp8266 3.1.2, PubSubClient 2.8, ArduinoJson 7.4). Entrambe le versioni compilano.

## Incidente del 16/10/2026 (rete Wi-Fi sbagliata)

Lo sketch originale aveva due reti: `ASUS_2.4` in un blocco commentato e `TP-Link_SalaTecnica`
attiva. Spostando le credenziali in `secrets.h` era stata presa la prima (commentata): il primo
caricamento di `floorpump_v2` non si collegava (ASUS_2.4 non arriva in sala tecnica) e non era più
raggiungibile via OTA. Recupero senza cavo: il TL-WA850RE è stato rinominato per qualche minuto
`ASUS_2.4` con la password dell'ASUS, la scheda si è collegata, è stata caricata la versione con
`TP-Link_SalaTecnica` e il nome dell'access point è stato ripristinato.
