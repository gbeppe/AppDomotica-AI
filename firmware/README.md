# Firmware delle schede della casa

Sketch Arduino delle schede ESP che il progetto comanda via MQTT. Si aggiungono qui man mano che
vengono verificati o riscritti (prossimo: VMC Brink `.23`).

| Scheda | Cartella | IP / OTA | Broker | Stato |
|---|---|---|---|---|
| Caminetto Palazzetti Idro 45 (ESP8266) | [`palazzetti/`](palazzetti/README.md) | `192.168.1.22`, `palazzetti` | `.15` | `palazzetti_v2` caricato il 14/10/2026 (precedente: `palazzetti_upgrade`) |
| Pompa pavimento (ESP8266) | [`floorpump/`](floorpump/README.md) | OTA `floorPump` | `.15` | in uso `pompapavimento_switch`; `floorpump_v2` pronto, non caricato |

## Regole

- **Le credenziali non entrano mai nel repository** (che è pubblico): ogni sketch le legge da
  `secrets.h`, escluso da `.gitignore`; nel repository c'è solo `secrets.h.example`.
  Controllo: `python3 firmware/check_firmware.py`.
- **Si lavora direttamente nel repository** (decisione del 14/10/2026), nella worktree di
  `feature/ui-topbar`: in Arduino IDE File → Open, Ctrl+L e il percorso reale
  `/home/giuseppe/.cache/Google/AndroidStudio2026.1.4/aia/agents/DomoPiAndroidApp-ai-mode/firmware/<dispositivo>/<sketch>/<sketch>.ino`
  (poi resta in File → Open Recent). **Non usare collegamenti simbolici**: Arduino IDE risolve il
  percorso reale, lo trova fuori dalla cartella aperta e mostra i file in sola lettura (lucchetto).
  Non aprire la copia del checkout `main` (`~/AndroidStudioProjects/DomoPiAndroidApp/firmware`):
  non ha i `secrets.h` e non è il branch di lavoro.
- Ogni cartella di sketch ha il proprio `secrets.h` locale (permessi 0600, ignorato da git).
- Nuovi sketch (VMC, pompa pavimento, …): cartella `firmware/<dispositivo>/<sketch>/` con il nome
  dello sketch uguale alla cartella; credenziali spostate in `secrets.h`; la vecchia copia in
  `~/Desktop/dev` si toglie dopo aver verificato che quella nel repository compila uguale.
- Il caricamento sulla scheda lo fa l'utente da Arduino IDE (porta di rete OTA), solo dopo
  averlo concordato; prima si annota la versione da ricaricare per tornare indietro.
- Compilazione di verifica senza IDE: l'`arduino-cli` incluso nell'AppImage di Arduino IDE
  (`<mount>/resources/app/lib/backend/resources/arduino-cli`) con
  `--config-file ~/.arduinoIDE/arduino-cli.yaml` e la FQBN indicata nel README della scheda.

## Arduino IDE sul PC `.40`

Versione 2.3.10, da aprire con l'icona di menu **"Arduino IDE 2.3.10"**
(`~/.local/share/applications/arduino-ide-2.desktop`; l'icona "Arduino IDE" apre la vecchia 1.8.16).

Su Ubuntu 26.04 l'IDE 2 non disegnava testi di editor, bottoni e menu a tendina
([arduino/arduino-ide#2957](https://github.com/arduino/arduino-ide/issues/2957)). Correzione del
14/10/2026: l'icona lancia l'AppImage con un fontconfig ristretto solo all'IDE
(`~/.config/arduino-fontconfig/fonts.conf`: DejaVu, Liberation, Noto, Open Sans, OpenType):
```sh
FONTCONFIG_FILE=~/.config/arduino-fontconfig/fonts.conf FONTCONFIG_PATH=~/.config/arduino-fontconfig \
  ~/Desktop/arduino-ide_2.3.10_Linux_64bit.AppImage --no-sandbox
```
Nelle preferenze resta anche il font dell'editor `'DejaVu Sans Mono', monospace`. Non sono
servite (e sono state annullate o sono innocue): `--disable-gpu`, X11, cache GPU, nome del font
GNOME, `GTK_THEME`, `--disable-remote-fonts`; il pacchetto `fonts-open-sans` è rimasto installato.
