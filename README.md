# Z-AI Android App

Z-AI is a modern Android application built with Jetpack Compose to monitor and control a smart home ecosystem managed by Node-RED, EmonCMS, and Tesla Powerwall.

## Features

### 📡 Multi-Broker MQTT Architecture
- Simultaneous connection to multiple brokers:
    - **Z-AI**: Handles AI logic, lighting, and system commands.
    - **EmonPi**: Handles energy telemetry and environmental sensors.
- Support for **Digital Twin** topic routing (App ↔ Bridge ↔ Hardware).

### ⚡ Energy Management
- **Live Energy Flow**: Real-time animation showing Solar production, Home consumption, Grid import/export, and **Tesla Powerwall** status.
- **Detailed History**: Interactive charts for energy production and consumption over time.

### ❄️ Climate & AI Control
- **AI Logic Management**: Intelligent management of air conditioning and ventilation based on surplus energy and humidex.
- **HVAC Visualization**: Responsive circular layout showing all heating and cooling systems.
- **Independent Thermostats**: Precise control (0.5°C steps) for multiple rooms.

### 🏊 Smart Pool
- **Interactive Pool View**: Clickable "Digital Twin" of the pool area to control pump, skimmer, and lighting with real-time visual feedback.

### 🏠 Home Security & More
- **IP Camera Integration**: Real-time MJPEG streams from TinyCam Pro.
- **Garage Control**: Pulse-based gate triggers.
- **Admin Mode**: Expert settings protected by PIN.

## Technical Stack
- **UI**: Jetpack Compose (Material 3).
- **Architecture**: MVI/MVVM with StateFlow.
- **Networking**: Eclipse Paho MQTT, Ktor.
- **Storage**: DataStore Preferences.
- **Build**: Gradle Kotlin DSL.

## Getting Started
1. Configure your Broker IP addresses in the **Z-AI** settings (Expert Mode required for some fields).
2. Ensure you are on the same network or connected via VPN (Tailscale recommended).
