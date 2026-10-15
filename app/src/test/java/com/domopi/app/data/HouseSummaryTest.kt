package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HouseSummaryTest {
    private val t = 1_791_700_000_000L

    private fun energy(vararg values: Pair<String, String>) = values.fold(EnergySmartState()) { state, (topic, payload) ->
        state.observe(topic, payload, t, true, "zara/interface/$topic")
    }

    private fun environment(vararg values: Pair<String, String>) =
        values.fold(EnvironmentSmartState()) { state, (room, payload) ->
            state.observe("env/$room/temperature/stat", payload, t, true, "zara/interface/env/$room/temperature/stat")
        }

    private val sunny = energy("energy/solar/power/stat" to "3240", "energy/home/consumption/stat" to "1100",
        "energy/battery/soc/stat" to "81.4", "energy/battery/power_raw/stat" to "-1500",
        "energy/grid/power_raw/stat" to "-640")

    @Test fun fullSummaryIsShortAndSpoken() {
        assertEquals("Living 22,1 gradi, fuori 16,9. Il fotovoltaico produce 3,2 kilowatt, la batteria è al " +
            "81 per cento e si sta caricando, la casa consuma 1,1 kilowatt, stai immettendo 640 watt in rete.",
            HouseSummary.text(sunny, environment("living" to "22.1", "outdoor" to "16.94"), connected = true))
    }

    @Test fun nightWithGridImport() {
        val night = energy("energy/solar/power/stat" to "0", "energy/home/consumption/stat" to "450",
            "energy/battery/soc/stat" to "20", "energy/battery/power_raw/stat" to "10",
            "energy/grid/power_raw/stat" to "430")
        assertEquals("Il fotovoltaico è fermo, la batteria è al 20 per cento, la casa consuma 450 watt, " +
            "stai importando 430 watt dalla rete.", HouseSummary.energyText(night))
    }

    @Test fun smallGridFlowIsIdle() {
        assertEquals("Non stai importando dalla rete.",
            HouseSummary.energyText(energy("energy/grid/power_raw/stat" to "-12")))
    }

    @Test fun dischargingWithoutSoc() {
        assertEquals("La batteria si sta scaricando, la casa consuma 900 watt.", HouseSummary.energyText(
            energy("energy/battery/power_raw/stat" to "900", "energy/home/consumption/stat" to "900")))
    }

    @Test fun missingDataIsLeftOutNotZero() {
        assertEquals("Casa non collegata: sono gli ultimi dati ricevuti. Fuori 17 gradi. Dati energetici non disponibili.",
            HouseSummary.text(EnergySmartState(), environment("outdoor" to "17.0"), connected = false))
        assertEquals("Temperature non disponibili.", HouseSummary.temperatures(EnvironmentSmartState()))
        assertEquals("Dati energetici non disponibili.",
            HouseSummary.energyText(energy("energy/solar/power/stat" to "abc")))
    }
}
