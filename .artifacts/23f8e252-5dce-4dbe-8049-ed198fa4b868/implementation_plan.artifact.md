# Fix Thermostat and Temperature Updates

The user reported that changes to the thermostat temperatures (and possibly other temperatures like the Puffer) are not being reflected in the app. Investigation revealed a bug in the MQTT message parser in `MqttManager.kt` where it fails to correctly extract the "property" from 5-part MQTT topics (e.g., `zara/interface/climate/thermostat_living/target_temperature`).

## User Review Required

> [!IMPORTANT]
> The fix involves a change to the core MQTT message parser. While this is necessary for thermostats, it also fixes updates for sensors, energy data, and heating systems that use a similar topic structure.

## Proposed Changes

### [MqttManager](file:///home/giuseppe/AndroidStudioProjects/DomoPiAndroidApp/app/src/main/java/com/domopi/app/data/MqttManager.kt)

#### [MODIFY] [MqttManager.kt](file:///home/giuseppe/AndroidStudioProjects/DomoPiAndroidApp/app/src/main/java/com/domopi/app/data/MqttManager.kt)
- Update the parser logic in `handleIncomingMessage` to correctly handle topics with 5 parts. This will ensure that properties like `target_temperature`, `current_temperature`, etc., are correctly extracted and passed to the handlers.

## Topics Used for Thermostats

As requested, here are the MQTT topics used by the app for managing the two thermostats (`thermostat_living` and `thermostat_bath`):

### Reading (Status Updates)
The app listens to these topics to update the UI:
- **Living Room:**
  - `zara/interface/climate/thermostat_living/current_temperature`
  - `zara/interface/climate/thermostat_living/target_temperature`
  - `zara/interface/climate/thermostat_living/min_temperature`
  - `zara/interface/climate/thermostat_living/max_temperature`
  - `zara/interface/climate/thermostat_living/power`
- **Bathroom:**
  - `zara/interface/climate/thermostat_bath/current_temperature`
  - `zara/interface/climate/thermostat_bath/target_temperature`
  - `zara/interface/climate/thermostat_bath/min_temperature`
  - `zara/interface/climate/thermostat_bath/max_temperature`
  - `zara/interface/climate/thermostat_bath/power`

### Writing (Commands)
The app publishes to these topics when you interact with the UI:
- **Living Room:**
  - `zara/interface/climate/thermostat_living/target_temperature/cmd`
  - `zara/interface/climate/thermostat_living/min_temperature/cmd`
  - `zara/interface/climate/thermostat_living/max_temperature/cmd`
- **Bathroom:**
  - `zara/interface/climate/thermostat_bath/target_temperature/cmd`
  - `zara/interface/climate/thermostat_bath/min_temperature/cmd`
  - `zara/interface/climate/thermostat_bath/max_temperature/cmd`

## Verification Plan

### Automated Tests
- I will perform a dry run of the parser logic with sample topics to ensure it works for 4, 5, and 6 part topics.

### Manual Verification
- Deploy the app and verify that changing thermostat temperatures (or other temperatures like the Puffer) externally results in an update in the app UI.
- Verify that changing the target temperature in the app correctly publishes to the `/cmd` topics.
