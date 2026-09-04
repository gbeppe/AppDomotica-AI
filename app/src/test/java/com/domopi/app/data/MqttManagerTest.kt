package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class MqttManagerTest {

    private lateinit var mqttManager: MqttManager
    private lateinit var handleIncomingMessageMethod: Method

    @Before
    fun setUp() {
        mqttManager = MqttManager()
        // Utilizzo di reflection sul codice di test per accedere al metodo private senza modificarne la visibilità in produzione
        handleIncomingMessageMethod = MqttManager::class.java.getDeclaredMethod(
            "handleIncomingMessage",
            String::class.java,
            String::class.java
        ).apply {
            isAccessible = true
        }
    }

    private fun processMessage(topic: String, payload: String) {
        handleIncomingMessageMethod.invoke(mqttManager, topic, payload)
    }

    @Test
    fun testEnergyDomainRoutingWithoutCollisions() {
        processMessage("zara/interface/energy/solar/power/stat", "2500")
        processMessage("zara/interface/energy/solar/surplus/stat", "1800")
        processMessage("zara/interface/energy/grid/power_raw/stat", "-500")
        processMessage("zara/interface/energy/grid/import/stat", "0")
        processMessage("zara/interface/energy/grid/export/stat", "500")
        processMessage("zara/interface/energy/battery/power_raw/stat", "300")
        processMessage("zara/interface/energy/battery/charge/stat", "300")
        processMessage("zara/interface/energy/battery/discharge/stat", "0")
        processMessage("zara/interface/energy/battery/soc/stat", "85")
        processMessage("zara/interface/energy/home/consumption/stat", "700")
        processMessage("zara/interface/energy/home/historical_average_band/stat", "650")
        processMessage("zara/interface/energy/ac/power/stat", "450")

        val energy = mqttManager.energyData.value
        val aiElec = mqttManager.aiManagedData.value.metricheElettriche

        // Verifico EnergyData
        assertEquals(2500f, energy.solarPower, 0.1f)
        assertEquals(700f, energy.homeConsumption, 0.1f)
        assertEquals(-500f, energy.gridPower, 0.1f)
        assertEquals(300f, energy.batteryPower, 0.1f)
        assertEquals(85f, energy.batterySoc, 0.1f)

        // Verifico MetricheElettriche (AiManagedData)
        assertEquals(2500f, aiElec.produzioneFvW, 0.1f)
        assertEquals(1800f, aiElec.surplusW, 0.1f)
        assertEquals(700f, aiElec.consumoCasaW, 0.1f)
        assertEquals(650f, aiElec.consumoMedioStoricoFasciaW, 0.1f)
        assertEquals(-500f, aiElec.gridPowerW, 0.1f)
        assertEquals(0f, aiElec.gridImportW, 0.1f)
        assertEquals(500f, aiElec.gridExportW, 0.1f)
        assertEquals(300f, aiElec.batteryPowerW, 0.1f)
        assertEquals(300f, aiElec.batteryChargeW, 0.1f)
        assertEquals(0f, aiElec.batteryDischargeW, 0.1f)
        assertEquals(85f, aiElec.powerwallSocPercent, 0.1f)
        assertEquals(450f, aiElec.consumoAcW, 0.1f)
    }

    @Test
    fun testEnvironmentDomainLivingHumidexAndReferenceHumidexSeparation() {
        // 1. Prima invio humidex_reference = 29.2
        processMessage("zara/interface/climate/control/humidex_reference/stat", "29.2")

        // 2. Poi invio living/humidex = 27.8
        processMessage("zara/interface/env/living/temperature/stat", "24.5")
        processMessage("zara/interface/env/living/humidex/stat", "27.8")

        val envState = mqttManager.environmentState.value
        val aiEnv = mqttManager.aiManagedData.value.metricheAmbientali

        // 3. Verifico che living/humidex NON sovrascriva humidex (riferimento logica)
        assertEquals(27.8f, envState.living.humidex, 0.1f)
        assertEquals(27.8f, aiEnv.humidexLiving, 0.1f)
        assertEquals(29.2f, aiEnv.humidex, 0.1f)
    }

    @Test
    fun testSolarAltitudeEnvironmentMapping() {
        processMessage("zara/interface/env/solar_altitude/stat", "42.5")

        val aiEnv = mqttManager.aiManagedData.value.metricheAmbientali
        assertEquals(42.5f, aiEnv.altitudineSole, 0.1f)
    }

    @Test
    fun testBedroomEnvironmentMapping() {
        processMessage("zara/interface/env/bedroom/temperature/stat", "22.3")
        processMessage("zara/interface/env/bedroom/humidex/stat", "25.1")

        val envState = mqttManager.environmentState.value
        val aiEnv = mqttManager.aiManagedData.value.metricheAmbientali

        assertEquals(22.3f, envState.bedroom.temperature, 0.1f)
        assertEquals(22.3f, aiEnv.tempCameraMatrimoniale, 0.1f)
        assertEquals(25.1f, envState.bedroom.humidex, 0.1f)
        assertEquals(25.1f, aiEnv.humidexBedroom, 0.1f)
    }

    @Test
    fun testFullStateTimestampRawLongParsing() {
        val epochTimestamp = 1788521708936L
        processMessage("zara/interface/climate/full_state/timestamp/stat", epochTimestamp.toString())
        processMessage("zara/interface/climate/full_state/data_ora_formattata/stat", "2026-09-04 14:30:00")

        val aiData = mqttManager.aiManagedData.value

        assertEquals(epochTimestamp, aiData.timestamp)
        assertEquals("2026-09-04 14:30:00", aiData.dataOraFormattata)
    }

    @Test
    fun testClimateDomainRegressionForThermostats() {
        // Imposto valori nei termostati per il test di regressione
        processMessage("zara/interface/climate/thermostat_bath/min_temperature/stat", "17.0")
        processMessage("zara/interface/climate/thermostat_bath/max_temperature/stat", "22.0")
        processMessage("zara/interface/climate/thermostat_bath/target_temperature/stat", "20.5")

        val initialBath = mqttManager.hvacState.value.thermostatBath
        val initialLiving = mqttManager.hvacState.value.thermostatLiving
        assertEquals(17.0f, initialBath.minTemp, 0.1f)
        assertEquals(22.0f, initialBath.maxTemp, 0.1f)
        assertEquals(20.5f, initialBath.targetTemp, 0.1f)

        // Inviolabilità: i topic non-thermostat e device sconosciuti non devono alterare né thermostatBath né thermostatLiving
        processMessage("zara/interface/climate/control/humidex_reference/stat", "30.0")
        processMessage("zara/interface/climate/full_state/timestamp/stat", "1788521708936")
        processMessage("zara/interface/climate/full_state/data_ora_formattata/stat", "2026-09-04 15:00:00")
        processMessage("zara/interface/climate/unknown_device/target_temperature/stat", "99")

        val bathAfterNonThermostat = mqttManager.hvacState.value.thermostatBath
        val livingAfterNonThermostat = mqttManager.hvacState.value.thermostatLiving
        assertEquals(17.0f, bathAfterNonThermostat.minTemp, 0.1f)
        assertEquals(22.0f, bathAfterNonThermostat.maxTemp, 0.1f)
        assertEquals(20.5f, bathAfterNonThermostat.targetTemp, 0.1f)
        assertEquals(initialLiving.targetTemp, livingAfterNonThermostat.targetTemp, 0.1f)

        // Verifico che i topic termostato reali funzionino ancora correttamente
        processMessage("zara/interface/climate/thermostat_living/current_temperature/stat", "23.5")
        processMessage("zara/interface/climate/thermostat_living/power/stat", "true")
        processMessage("zara/interface/climate/thermostat_bath/target_temperature/stat", "21.5")

        val living = mqttManager.hvacState.value.thermostatLiving
        val bath = mqttManager.hvacState.value.thermostatBath

        assertEquals(23.5f, living.currentTemp, 0.1f)
        assertTrue(living.power)
        assertEquals(21.5f, bath.targetTemp, 0.1f)
    }

    @Test
    fun testVmcExtendedMapping() {
        processMessage("zara/interface/ventilation/vmc/speed/stat", "3")
        processMessage("zara/interface/ventilation/vmc/reason/stat", "HUMIDEX_ELEVATO")
        processMessage("zara/interface/ventilation/vmc/outdoor_humidex/stat", "31.4")
        processMessage("zara/interface/ventilation/vmc/outdoor_temperature/stat", "29.8")

        val vmc = mqttManager.aiManagedData.value.statoVmc
        val hvacVmc = mqttManager.hvacState.value.vmc

        assertEquals(3, vmc.velocitaAttuale)
        assertEquals(3, hvacVmc.speed)
        assertEquals("HUMIDEX_ELEVATO", vmc.motivoLogica)
        assertEquals(31.4f, vmc.humidexEsterno, 0.1f)
        assertEquals(29.8f, vmc.temperaturaEsternaC, 0.1f)
    }

    @Test
    fun testSeasonSynchronization() {
        processMessage("zara/interface/logica_controllo/stagione_attuale/stat", "ESTATE")

        val aiData = mqttManager.aiManagedData.value

        assertEquals("ESTATE", aiData.stagioneAttiva)
        assertEquals("ESTATE", aiData.logicaControllo.stagioneAttuale)
    }

    @Test
    fun testLegacyTopicIgnored() {
        val initialEnergy = mqttManager.energyData.value
        processMessage("TeslaPowerwall/solar_instant_power", "9999")
        val currentEnergy = mqttManager.energyData.value
        assertEquals(initialEnergy, currentEnergy)
    }

    @Test
    fun testNonInterfaceTopicIgnored() {
        val initialEnv = mqttManager.environmentState.value
        processMessage("zara/domotics/sensors/living", "99")
        val currentEnv = mqttManager.environmentState.value
        assertEquals(initialEnv, currentEnv)
    }

    @Test
    fun testUnknownPublicDeviceIsolation() {
        processMessage("zara/interface/climate/thermostat_bath/target_temperature/stat", "20.5")
        val hvacStateBefore = mqttManager.hvacState.value

        processMessage("zara/interface/climate/unknown_device/target_temperature/stat", "99")
        val hvacStateAfter = mqttManager.hvacState.value

        assertEquals(hvacStateBefore, hvacStateAfter)
    }

    @Test
    fun testObsoleteHeatPumpTopicsIgnored() {
        val hvacStateBefore = mqttManager.hvacState.value

        processMessage("zara/interface/heating/heat_pump/enabled/stat", "true")
        processMessage("zara/interface/heating/heat_pump/running/stat", "true")
        processMessage("zara/interface/heating/heat_pump/solar_divert/stat", "true")
        processMessage("zara/interface/heating/heat_pump/target_temperature/stat", "55")

        val hvacStateAfter = mqttManager.hvacState.value

        assertEquals(hvacStateBefore, hvacStateAfter)
    }
}
