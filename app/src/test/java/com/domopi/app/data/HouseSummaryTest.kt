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
        assertEquals("Living 22,1 gradi, fuori 16,9. Il fotovoltaico produce 3,2 kilowatt, la casa consuma " +
            "1,1 kilowatt. Batteria al 81 per cento, in carica. Immissione in rete: 640 watt.",
            HouseSummary.text(sunny, environment("living" to "22.1", "outdoor" to "16.94"), connected = true))
    }

    @Test fun nightWithGridImport() {
        val night = energy("energy/solar/power/stat" to "0", "energy/home/consumption/stat" to "450",
            "energy/battery/soc/stat" to "20", "energy/battery/power_raw/stat" to "10",
            "energy/grid/power_raw/stat" to "430")
        assertEquals("Fotovoltaico fermo, la casa consuma 450 watt. Batteria al 20 per cento. " +
            "Prelievo dalla rete: 430 watt.", HouseSummary.energyText(night))
    }

    @Test fun smallGridFlowIsIdle() {
        assertEquals("Nessun prelievo dalla rete.",
            HouseSummary.energyText(energy("energy/grid/power_raw/stat" to "-12")))
    }

    @Test fun missingDataIsLeftOutNotZero() {
        assertEquals("Casa non collegata: sono gli ultimi dati ricevuti. Fuori 17 gradi. Dati energetici non disponibili.",
            HouseSummary.text(EnergySmartState(), environment("outdoor" to "17.0"), connected = false))
        assertEquals("Temperature non disponibili.", HouseSummary.temperatures(EnvironmentSmartState()))
        assertEquals("Dati energetici non disponibili.",
            HouseSummary.energyText(energy("energy/solar/power/stat" to "abc")))
    }
}
