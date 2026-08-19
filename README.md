# DomoPi Android App

DomoPi is a modern Android application built with Jetpack Compose to monitor and control a smart home ecosystem managed by Node-RED, EmonCMS, and Tesla Powerwall.

## Features

### 📡 Multi-Broker MQTT Architecture
- Simultaneous connection to multiple brokers:
    - **DomoPi**: Handles AI logic, lighting, and system commands.
    - **EmonPi**: Handles energy telemetry and environmental sensors.
- Support for **Digital Twin** topic routing (App ↔ Bridge ↔ Hardware).

### ⚡ Energy Management
- **Live Energy Flow**: Real-time animation showing Solar production, Home consumption, Grid import/export, and **Tesla Powerwall** charge/discharge status.
- **Historical Analysis**: 6h and 24h charts fetched via EmonCMS API, showing power trends (Watts) and Battery State of Charge (%).

### 🤖 AI Climate Control
- Monitoring of "AI Managed" climate logic.
- Quick toggle for Auto/Manual mode directly from the dashboard.
- Detailed configuration of AI parameters (min run/off time, humidex targets, VMC speeds).

### 🏠 Home Automation
- **Lighting**: Logical scene control (TV Mode, Sleep Mode) and individual device toggles with real-time status feedback.
- **Environment**: Precise monitoring of temperature and humidity across multiple rooms (Living, Bedroom, Outdoor) with high-precision gauges (1 decimal point).
- **Video Surveillance**: MJPEG stream integration from TinyCam Pro.

### 🛠 Tech & Diagnostics
- **Connectivity Hub**: Top bar indicators for Local/Remote mode and broker connection status.
- **Traffic Diagnosis**: Live MQTT message log for real-time debugging.
- **Smart Configuration**: Centralized settings for IP, ports, and credentials with unsaved changes protection.

## Requirements
- Android 8.0 (API 26) or higher.
- MQTT Brokers (DomoPi & EmonPi).
- EmonCMS server for energy history.

## Development
Built using:
- **Jetpack Compose** for UI.
- **Kotlin Coroutines & Flow** for reactive data handling.
- **Paho MQTT Client** for IoT communication.
- **Ktor Client** for EmonCMS API integration.
- **DataStore** for persistent local settings.
