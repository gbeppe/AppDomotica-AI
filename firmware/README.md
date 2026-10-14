# Firmware delle schede della casa

Sketch Arduino delle schede ESP che il progetto comanda via MQTT. Si aggiungono qui man mano che
vengono verificati o riscritti (prossimi: VMC Brink `.23`, pompa pavimento).

| Scheda | Cartella | IP / OTA | Broker | Stato |
|---|---|---|---|---|
| Caminetto Palazzetti Idro 45 (ESP8266) | [`palazzetti/`](palazzetti/README.md) | `192.168.1.22`, `palazzetti` | `.15` | `palazzetti_v2` caricato il 14/10/2026 |

## Regole

- **Le credenziali non entrano mai nel repository** (che è pubblico): ogni sketch le legge da
  `secrets.h`, escluso da `.gitignore`; nel repository c'è solo `secrets.h.example`.
  Controllo: `python3 firmware/check_firmware.py`.
- La copia di lavoro per Arduino IDE sta in `~/Desktop/dev/<sketch>/` (cartella degli sketch
  dell'IDE), con il suo `secrets.h` (permessi 0600). Dopo ogni modifica le due copie dello sketch
  devono restare identiche (`diff`).
- Il caricamento sulla scheda lo fa l'utente da Arduino IDE (porta di rete OTA), solo dopo
  averlo concordato; prima si annota la versione da ricaricare per tornare indietro.
- Compilazione di verifica senza IDE: l'`arduino-cli` incluso nell'AppImage di Arduino IDE
  (`<mount>/resources/app/lib/backend/resources/arduino-cli`) con
  `--config-file ~/.arduinoIDE/arduino-cli.yaml` e la FQBN indicata nel README della scheda.

## Arduino IDE sul PC `.40`

Versione 2.3.10: `~/Desktop/arduino-ide_2.3.10_Linux_64bit.AppImage --no-sandbox` (l'icona del
menu apre ancora la vecchia 1.8.16). Se l'editor mostra il riquadro vuoto senza testo, impostare
in File → Preferences il font dell'editor `'DejaVu Sans Mono', monospace` (corretto il 14/10/2026).
