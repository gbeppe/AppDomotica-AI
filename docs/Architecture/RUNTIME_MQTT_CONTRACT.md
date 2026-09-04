# Runtime MQTT Contract

**Project:** DomoPiAndroidApp  
**Status:** Baseline validated after WP-D1 / WP-D2  
**Scope:** Runtime contract between Android and Node-RED via MQTT

## 1. Architectural boundary

The Android app communicates only through the public MQTT namespace:

`zara/interface/...`

Android must not depend on legacy MQTT topics, Node-RED internal wiring, or the legacy DomoticsAI registry YAML.

The current runtime path is:

`DomoPiAndroidApp → MQTT → Digital Twin Router → legacy Node-RED/device topics`

and, for state updates:

`legacy Node-RED/device topics → Digital Twin Router → zara/interface/.../stat → DomoPiAndroidApp`

## 2. Source of truth

The effective runtime contract is the set of public `zara/interface/...` topics implemented by:

- the active Node-RED Digital Twin Router;
- the Android MQTT publisher/subscriber logic.

The file `config/registry/digital-twin-registry.yaml` belongs to the abandoned DomoticsAI project and is **not** part of the current DomoPiAndroidApp architecture.

Do not add that YAML as an Android dependency or use it to generate runtime mappings.

## 3. Android rules

Android:

- subscribes only to public `zara/interface/...` state topics;
- publishes commands only to public `zara/interface/.../cmd` topics;
- does not subscribe to or publish legacy topics directly;
- does not parse `casa/clima/stato_completo`;
- does not know Node-RED legacy device topics;
- must preserve the current public topic names unless a coordinated Android + Node-RED contract change is explicitly planned.

## 4. Public state domains currently consumed

Android subscribes to these public namespaces:

- `zara/interface/lights/#`
- `zara/interface/pool/#`
- `zara/interface/env/#`
- `zara/interface/energy/#`
- `zara/interface/heating/#`
- `zara/interface/climate/#`
- `zara/interface/ai/#`
- `zara/interface/ai_climate/#`
- `zara/interface/fireplace/#`
- `zara/interface/ventilation/#`
- `zara/interface/settings/#`
- `zara/interface/garage/#`
- `zara/interface/stato_condizionatore/#`
- `zara/interface/logica_controllo/#`

WP-D1 verified that all current non-obsolete public runtime states are handled by Android.

The remaining `heating/heat_pump/...` entries in Node-RED refer to a removed physical device and are classified as obsolete Node-RED entries, not Android gaps.

## 5. Public command contract

WP-D1 verified that Android can generate all current non-obsolete Router commands.

The only real contract gap found was the lights scene command. It was resolved in WP-D2.

### Light scenes

Public topic:

`zara/interface/lights/scene/cmd`

Allowed semantic payloads:

- `tv`
- `sleep`
- `all_on`
- `all_off`

Android must publish the semantic scene identifier unchanged.

Node-RED is responsible for translating the semantic command into the existing legacy scene automation.

Validated end-to-end path:

`Android button → zara/interface/lights/scene/cmd → Digital Twin Router → Scene Dispatcher → existing legacy scene logic → physical effect`

All four scenes were verified from the Android UI and passed.

## 6. Command ingress ownership

The active Digital Twin Router is the single public command ingress.

Do not add parallel MQTT command consumers for `zara/interface/...` unless required by a new architectural decision.

The scene bridge deliberately reuses the Router and existing legacy Node-RED scene logic rather than duplicating device-control logic.

## 7. Change policy

Any future change to a public `zara/interface/...` topic must be treated as a runtime contract change.

Before changing a public topic:

1. identify Android publishers/subscribers;
2. identify the active Node-RED Router mapping;
3. verify whether legacy consumers are affected;
4. update Android and Node-RED in a coordinated way;
5. perform MQTT and physical end-to-end validation;
6. commit the change atomically.

Do not refactor or rename public topics only for naming consistency.

## 8. Validated baseline

WP-D1 — Runtime Contract Audit: **COMPLETED**  
WP-D2.1 — Android semantic scene commands: **PASS**  
WP-D2.2 — Node-RED Scene Command Bridge: **PASS**  
Android → MQTT → Node-RED → physical scene E2E: **PASS**

Android scene baseline commit:

`5013ff6 fix(android): preserve semantic light scene commands`

The corresponding Node-RED D2 baseline was committed after the final full-flow export and E2E validation.
