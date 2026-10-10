# Production Node-RED Climate Snapshot (.20)

- **Source**: Production Node-RED instance `.20`
- **Snapshot Date**: 2026-09-22
- **Nature of Exports**: Selected Function-node exports. They are **NOT** a complete Node-RED flow backup. Surrounding MQTT nodes, broker config nodes, and complete wiring are not necessarily included.
- **Purpose**: Version the authoritative production Function-node logic used by DomoticsAI.

## Exact Node IDs and Names
1. `61992c8879c11f66` — Machine Learning domotico locale ad alta precisione
2. `5a7c1d360710ca21` — Digital Twin Router
3. `18cd61655da131ad` — Gestore Impostazioni UI & Android

---

## HUMIDEX B2 CONTRACT

- **Climate Controller owns/calculates**:
  - `user_humidex_comfort_hysteresis`
  - `user_night_dry_humidex_margin`
  - `nightDryHumidexThreshold`
  - `sogliaSpegnimentoComfort`

- **Settings Consumer persists**:
  - `humidex_comfort_hysteresis`
  - `night_dry_humidex_margin`

- **Digital Twin Router exposes commands**:
  - `zara/interface/ai/humidex_comfort_hysteresis/cmd`
  - `zara/interface/ai/night_dry_humidex_margin/cmd`

- **Authoritative states**:
  - `zara/interface/logica_controllo/ampiezza_isteresi_humidex/stat`
  - `zara/interface/logica_controllo/soglia_spegnimento_comfort/stat`
  - `zara/interface/logica_controllo/night_dry_humidex_margin/stat`
  - `zara/interface/logica_controllo/night_dry_humidex_threshold/stat`

---

## Architectural Rules

- **Controller calculates; Android displays.**
- Android must never reconstruct decision thresholds locally.
- The legacy `+1.0 / SOC > 75` branch remains present in the Controller snapshot (`climate-controller.function.json`) but is intentionally **NOT** exposed as a configurable parameter because the causal audit established that the night assignment to `transizionePermessa` is not consumed by the actual `NIGHT_DRY` execution branch.

---

## Full Tab Exports (`flows/`)

- **Source**: Production Node-RED instance `.20`, editor export of three full tabs.
- **Export Date**: 2026-10-10
- **Sanitization**: no node `credentials` were present in the export. The home
  coordinates in the Open-Meteo URL (`ai-climate.flow.json`) are replaced by the
  placeholders `${HOME_LAT}` / `${HOME_LON}`. LAN addresses are kept.
- **Not a restore artifact**: broker/MySQL credentials and other tabs (scenes,
  lights, Alexa) are not included. `link out` nodes pointing to other tabs show
  as unresolved.

| File | Tab id | Tab | Nodes |
|---|---|---|---|
| `digital-twin-bridge.flow.json` | `294638a5b3d82009` | Digital Twin Bridge | 85 |
| `ai-climate-predictive-reserve.flow.json` | `dd41aeb2cd0f9048` | AI Climate - Predictive Reserve | 109 |
| `ai-climate.flow.json` | `2cc001ef3d59afd2` | AI climate | 146 |

The three Function-node snapshots in `climate/` match these exports, except the
Digital Twin Router, which in production also carries
`actor_policy: 'dashboard_only_v2'` (semantic bridge `actor_policy` patch).

### Predictive Reserve runtime path

Climate Controller output 6 → `link call` (2 s timeout) → PR SHADOW Engine →
PR Final SHADOW Decision → PR Arbitration Snapshot → PR Shared Arbitration Gate
→ PR Pre-start Result → return → `AI Climate NIGHT_DRY Commit` →
`zara/domotics/AC/command`.

- `pr_control_enabled` is the Android toggle
  (`zara/interface/predictive_reserve/control_enabled/cmd`).
- `pr_operating_mode` (`SHADOW`/`ACTIVE`) is set only by an editor inject and is
  not exposed to Android. Control ON with mode SHADOW denies every new
  OFF → NIGHT_DRY start (`PR_NOT_AUTHORIZED`).
- Only `eco26dry` is a qualified regime in the Shared Arbitration Gate.
- No node writes `predictive_reserve_shadow.log` any more.

### Disconnected nodes kept in production

Not wired and not executed, kept by the user as history: six older
"Machine Learning domotico locale ad alta precisione" copies, a second Digital
Twin Router (`d6fd86d961bc3efc`), duplicate PR SHADOW Engine / Final SHADOW
Decision / Arbitration Snapshot nodes and two "Prepara INSERT Clima" nodes.

### Refreshing the exports

Export each tab from the `.20` editor (Export → current flow → JSON), replace
the coordinates as above, verify that no `credentials` object is present and
commit the three files together.
