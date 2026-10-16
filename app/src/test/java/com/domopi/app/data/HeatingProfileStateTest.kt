package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatingProfileStateTest {
    // Payloads as published by HEATING-PROFILE.2 on .20.
    private val profile = """{"schema":"domopi.heating_profile.v1","profile":"ridotto","mode":"auto","auto_profile":"ridotto",
        "outdoor_mean_72h":17.2,"hours_of_data":60,"thresholds":{"pieno_below":10},"timer_mode":"shadow"}"""
    private val plan = """{"schema":"domopi.heating_timer_plan.v1","profile":"ridotto","timer_mode":"shadow","thermostats":{
        "thermostat_living":{"inside":false,"value":17.5,"slot":null,"min":17.5,"max":18,"next":{"day_offset":0,"at":"17:00","inside":true,"value":18}},
        "thermostat_bath":{"inside":false,"value":17,"slot":null,"min":17,"max":17.5,"next":{"day_offset":0,"at":"17:00","inside":true,"value":17.5}}}}"""
    private val learning = """{"schema":"domopi.heating_learning.v1","learning_active":false,"corrections":[
        {"at":"2026-10-16T12:51:34.418Z","thermostat":"thermostat_living","value":22,"previous":18,"timer_value":17.5,"profile":"ridotto","outdoor_mean_72h":23.3}]}"""

    private val state = HeatingProfileState().withProfile(profile).withPlan(plan).withLearning(learning)

    @Test fun parsesTheThreeTopics() {
        assertEquals("ridotto", state.profile)
        assertTrue(state.automatic)
        assertTrue(state.shadow)
        assertEquals(17.2, state.outdoorMean72h!!, 0.001)
        assertEquals(NextChange(0, "17:00", true, 18.0), state.plan["thermostat_living"]!!.next)
        assertEquals(1, state.corrections.size)
        assertFalse(state.learningActive)
    }

    @Test fun cardTexts() {
        assertEquals("Media esterna 3 giorni: 17,2 °C (Ridotto tra 10 e 16 °C)", HeatingProfileText.reason(state))
        assertEquals("Prossimo: 17:00 · soggiorno → 18,0 °C · bagno → 17,5 °C", HeatingProfileText.next(state))
        assertEquals("Timer: fuori fascia → minimo 17,5 °C · prossima fascia 17:00 → 18,0 °C",
            HeatingProfileText.timerLine(state.plan["thermostat_living"]))
        assertEquals("16/10 14:51 · soggiorno a 22,0 °C (era 18,0) · esterno 23,3 °C · Ridotto",
            HeatingProfileText.correction(state.corrections.first()))
    }

    @Test fun notEnoughDataAndForcedProfile() {
        val early = HeatingProfileState().withProfile(profile.replace("\"hours_of_data\":60", "\"hours_of_data\":5"))
        assertEquals("Raccolta dati esterni: 5 ore su 48. Fino ad allora resta Ridotto.", HeatingProfileText.reason(early))
        val forced = HeatingProfileState().withProfile(profile.replace("\"mode\":\"auto\"", "\"mode\":\"pieno\""))
        assertFalse(forced.automatic)
        assertEquals("Scelto da te: resta così finché non torni ad Automatico.", HeatingProfileText.reason(forced))
    }

    @Test fun invalidPayloadsKeepTheState() {
        assertEquals(state, state.withProfile("not json").withPlan("{}").withLearning("[]"))
        assertNull(HeatingProfileState().profile)
        assertFalse(HeatingProfileState().known)
    }

    @Test fun profileCommandOnlyForKnownModes() {
        val manager = MqttManager()
        assertTrue(manager.setHeatingProfileMode("pieno"))
        assertFalse(manager.setHeatingProfileMode("turbo"))
    }
}
