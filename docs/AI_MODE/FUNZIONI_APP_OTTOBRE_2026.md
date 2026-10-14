# Funzioni dell'app aggiunte a ottobre 2026

Funzioni dell'app Android (branch `feature/ui-topbar`) che non hanno un documento di dominio
dedicato. Per AI Smart vedere anche [`INTERAZIONE_QUERY_SMART.md`](INTERAZIONE_QUERY_SMART.md).

## AI Smart — fase 3.5 (riepiloghi proattivi)

| Parte | Cosa fa | Dove nel codice | Commit |
|---|---|---|---|
| A — Riepilogo all'apertura | Il riquadro "Stato della domotica" mostra temperature (living, esterno) ed energia (FV, consumo, batteria, rete) in frasi brevi; dati mancanti omessi, flussi sotto 50 W = fermo; solo scritto ("Leggi riepilogo" per la voce) | `data/HouseSummary.kt` | `0f3d3f7` |
| B — Avvisi | Banner in cima a ogni schermata, solo scritto: "Casa non collegata" (dopo 30 s, solo se era collegata) e "Casa di nuovo collegata"; batteria al 100% (riarmo sotto 95%) e sotto 20% (riarmo sopra 25%); max uno per tipo ogni 30 min; mai per lo stato trovato all'apertura | `data/HouseAlerts.kt`, `ui/components/HouseAlertBanner.kt` | `acd3eec` |
| C — "Com'è andata oggi" | Pulsante: totali di oggi (prodotto, consumato, prelevato, immesso) e previsione di domani, calcolati dal backend senza Groq (`GET /v1/summary/day`) | `backend/house_ai/day_summary.py`, `AiSmartScreen.kt` | `5140a4b`, `67663f3` |

Messaggi d'errore distinti (troppe domande, Groq non risponde, server non raggiungibile, nome non
risolto fuori casa senza Tailscale): `data/HouseAiErrors.kt` (`51eb224`, `528c557`).

## Home

- **"Importato ieri / oggi"**: letto da EmonCMS all'avvio e aggiornato ogni 15 minuti via MQTT dal
  servizio `house-ai-grid-import` di `.20` ([`PRELIEVO_RETE_GIORNALIERO.md`](PRELIEVO_RETE_GIORNALIERO.md)).
- **Senza broker** (fuori casa senza Tailscale) il riquadro "Stato casa" e il pulsante
  "Climatizzazione AI" mostrano "—" invece di valori predefiniti che sembravano veri (0,0°,
  "ATTIVATO"): `data/DashboardText.kt`.
- **Carosello**: 10 schede; la decima è **IRRIGAZIONE**. La pagina iniziale è un multiplo di 10
  (`MainActivity`, `lastDashboardPage`).

## Piscina interattiva (`64f2893`)

La foto `pool_background.jpg` è disegnata in grigio; ogni elemento acceso è ridisegnato a colori
dentro la sua forma: acqua (ellisse, con tinta azzurra), skimmer e pompa (ovali), pedana (anello +
passerella meno gli altri elementi, con gli 8 faretti illuminati). Un tocco su un elemento lo
accende/spegne (`lucipiscina`, `skimmerpiscina`, `pompapiscina`, `lucipedanapiscina`).

- Forme misurate sulla foto in coordinate 0–1 e riconoscimento del tocco: `ui/components/PoolGeometry.kt`
  (test `PoolGeometryTest`); disegno: `ui/components/PoolInteractiveComponent.kt`.
- Approvata dall'utente tramite una simulazione web (artifact privato
  `https://claude.ai/artifact/8LYKhUBS2QPHyBJaQKoXbS`) con la stessa geometria.

## Irrigazione (`6dd6612`, `749b62b`)

Schermata "Irrigazione" e scheda del carosello per OpenSprinkler, collegate al flow Node-RED
IRRIGATION.1 ([`../../backend/nodered/irrigation/README.md`](../../backend/nodered/irrigation/README.md)).

- Zone (avvio 1–60 minuti con slider, arresto, minuti residui), programmi (avvio con conferma),
  "Ferma tutto" (con conferma), interruttore del controller, esito di ogni comando.
- Comandi disattivati se la casa non è collegata o lo stato è più vecchio di 90 s; i comandi non
  vengono mai accodati offline.
- Codice: `data/IrrigationState.kt`, `ui/screens/IrrigationScreen.kt`; topic `zara/interface/irrigation/...`
  (abbonamento in `MqttManager.subscribeToUnifiedTopics`).

## Telecamere (`558bc00`)

Lettore nativo TinyCam al posto della WebView (che non mostrava l'MJPEG): istantanea ogni 1,5 s
nella scheda del carosello (solo quando visibile), flusso MJPEG continuo nella schermata
Telecamere. Messaggi chiari per server spento, credenziali errate, telecamera sconosciuta.
Elenco telecamere configurabile in Configurazione → TinyCam Pro (`Nome=ID, Nome=ID`; la prima va
nella Home). Codice: `data/TinyCam.kt` (`TinyCamClient`, `MjpegFrameReader`),
`ui/components/CameraStreamComponent.kt`.

## Comandi manuali (`e22c58b`, `bb2a4d0`)

Slider livello caminetto e massimo VMC notturno inviano un solo comando al rilascio (valore
arrotondato, solo se cambiato). Orari Avvio/Fine caminetto inviati solo completi (HH:MM, anche
senza ":"), alla conferma da tastiera o all'uscita dal campo. Codice: `data/ControlInputs.kt`.

## Credenziali e campi segreti (`b85fdb1`, `42fdb71`, `b470d26`)

- Password MQTT e TinyCam, PIN admin, chiave EmonCMS, token House AI solo nel deposito cifrato
  (`data/CredentialStore.kt`, `SettingsManager.loadSecrets` migra i vecchi valori in chiaro).
- Senza PIN salvato, il primo sblocco chiede di sceglierne uno; la pressione lunga sul lucchetto
  lo cancella.
- Campi segreti `ui/components/SecretTextField.kt`: pulsante Incolla (svuota gli appunti) e
  Mostra/Nascondi.
- MQTT: credenziali inviate solo in coppia; se cambiano, l'app si ricollega da sola.
- Test: `NoSecretsInCodeTest` (nessun segreto nel codice, verificato anche per impronta SHA-256).
