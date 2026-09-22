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
