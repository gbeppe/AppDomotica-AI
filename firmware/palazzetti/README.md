# Caminetto Palazzetti — scheda ESP8266

Il caminetto (Palazzetti Idro 45) non ha un'interfaccia dati: un ESP8266 a `192.168.1.22`
simula le pressioni dei tasti del pannello con 6 relè. Sketch attuale:
[`palazzetti_v2/palazzetti_v2.ino`](palazzetti_v2/palazzetti_v2.ino), caricato il **14/10/2026**
e verificato (riallineamento all'avvio, cambio livello 2→3 sul display).

## Catena dei comandi

App → `zara/interface/fireplace/main/level/cmd` → Digital Twin Router (`.20`) →
`alexa/caminetto` → Node-RED di `.15` → `/esp8266/palazzetti/command` sul broker di **`.15`** →
scheda. Il ritorno nell'app (`level/stat`) viene da `emon/focolare/powerraw`, che è il livello
**richiesto** (1–6), non una misura: il caminetto non comunica il proprio stato.
Accensione: `fireplace/main/power` → `zara/domotics/palazzetti/acceso`.

## Topic (broker `.15`, base `/esp8266/palazzetti`)

| Topic | Direzione | Contenuto |
|---|---|---|
| `/command` | verso la scheda, **retained** | `on`, `off`, `1`…`6` |
| `/power` | verso la scheda | `1`…`6`: riallinea il livello presunto senza premere tasti |
| `/getstatus` | verso la scheda | qualunque valore: ripubblica lo stato (Node-RED di `.15` lo usa) |
| `/hello` | dalla scheda, retained | JSON di stato: chiavi storiche (`"Stufa: Livello Potenza "`, `"Stufa: acceso "` con lo spazio finale, IP, MAC, RSSI) + `ultimo comando`, `in esecuzione`, `Firmware`, `Uptime s` |
| `/online` | dalla scheda, retained | `online` / `offline` (last will) |

## Relè e sequenze

Pin (relè attivi ALTI): on 14, off 12, giù 16, su 13, meno 4, più 5. Pressione breve 200 ms,
lunga 3,5 s, pausa 500 ms dopo ogni pressione.

| Comando | Sequenza |
|---|---|
| ritorno al menu | 5 × off (+500 ms) |
| `on` | menu, on lungo, meno (voce "pellet") |
| `off` | menu, off lungo, giù (voce "spegnimento") |
| livello N | menu, on, on, (più/meno × differenza), on (conferma), off, off — ~7–10 s |

Il livello è **presunto** (contato dai comandi) e salvato in EEPROM. Se qualcuno lo cambia dal
pannello, basta inviare una volta dall'app il livello mostrato sul display. Premendo "meno" a 1 il
pannello riparte da 6: per questo non si può "azzerare" partendo dal minimo (`LEVEL_HOMING`
resta `false`); l'utente ha scelto di non usare la strada più corta sfruttando il giro.

## Cosa cambia rispetto alla versione precedente (`palazzetti_upgrade`, aprile 2026)

- Nessuna attesa bloccante: durante una sequenza MQTT e OTA restano attivi; i livelli arrivati
  nel frattempo si riducono all'ultimo.
- **`/command` retained**: il broker lo reinvia a ogni riconnessione e la vecchia versione lo
  rieseguiva (pressioni non volute, livello sfasato). Ora i comandi ricevuti nei primi 3 s dopo
  la sottoscrizione aggiornano solo lo stato presunto.
- Livello presunto in EEPROM (prima tornava a 6 a ogni riavvio).
- Riconnessione Wi-Fi/MQTT senza blocchi; topic `/online` con last will.
- Relè a riposo prima di tutto all'avvio e durante l'OTA.
- Credenziali in `secrets.h`.

## Caricare o tornare indietro

1. Copia di lavoro: `~/Desktop/dev/palazzetti_v2/` (con `secrets.h`, da
   `secrets.h.example`). Versione precedente: `~/Desktop/dev/palazzetti_upgrade/palazzetti/`.
2. Arduino IDE: **Generic ESP8266 Module**, Flash Size **1MB (FS:64KB OTA:~470KB)**, Flash Mode
   **DOUT**, Crystal 26 MHz; porta di rete **palazzetti at 192.168.1.22**; Upload.
   (Lo sketch pesa ~332 KB e sta nei ~470 KB disponibili per l'OTA.)
3. Verifica: deve comparire `/online online` e un `/hello` con `"Firmware":"palazzetti_v2"`.
4. Per tornare indietro: caricare allo stesso modo `palazzetti_upgrade`.

Compilazione di verifica: FQBN `esp8266:esp8266:generic:eesz=1M64,FlashMode=dout,ResetMethod=nodemcu`
(core esp8266 3.1.2, PubSubClient 2.8, ArduinoJson 7.4).

## Ascoltare la scheda

Dal PC `.40`, leggendo le credenziali da `secrets.h` senza mostrarle:
```sh
cd ~/Desktop/dev/palazzetti_v2
U=$(sed -n 's/#define MQTT_USER "\(.*\)"/\1/p' secrets.h)
P=$(sed -n 's/#define MQTT_PASSWORD "\(.*\)"/\1/p' secrets.h)
mosquitto_sub -h 192.168.1.15 -u "$U" -P "$P" -v -t '/esp8266/palazzetti/#'
```
