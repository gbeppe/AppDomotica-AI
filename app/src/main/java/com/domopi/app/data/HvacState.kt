package com.domopi.app.data

import kotlinx.serialization.Serializable

@Serializable
data class HvacState(
    val ac: AcStatus = AcStatus(),
    val vmc: VmcStatus = VmcStatus(),
    val palazzetti: PalazzettiStatus = PalazzettiStatus(),
    val boiler: BoilerStatus = BoilerStatus(),
    val floorHeating: FloorHeatingStatus = FloorHeatingStatus(),
    val thermostatLiving: ThermostatStatus = ThermostatStatus(),
    val thermostatBath: ThermostatStatus = ThermostatStatus()
)

@Serializable
data class ThermostatStatus(
    val currentTemp: Float = 0f,
    val targetTemp: Float = 20f,
    val minTemp: Float = 16f,
    val maxTemp: Float = 22f,
    val power: Boolean = false
)

@Serializable
data class AcStatus(
    val mode: String = "OFF",
    val tempSet: Float = 24f,
    val active: Boolean = false
)

@Serializable
data class VmcStatus(
    val speed: Int = 1,
    val active: Boolean = true
)

@Serializable
data class PalazzettiStatus(
    val active: Boolean = false,
    val level: Int = 1,
    val mode: String = "Disattivato",
    val startTime: String = "--:--",
    val stopTime: String = "--:--",
    val autoPower: Boolean = false,
    val waterTemp: Float = 0f
)

@Serializable
data class BoilerStatus(
    val modulation: Int = 0,
    val active: Boolean = false
)

@Serializable
data class FloorHeatingStatus(
    val enabled: Boolean = false,
    val pumpActive: Boolean = false
)
