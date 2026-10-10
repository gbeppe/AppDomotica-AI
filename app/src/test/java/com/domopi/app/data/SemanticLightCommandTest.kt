package com.domopi.app.data

import org.junit.Assert.*
import org.junit.Test

class SemanticLightCommandTest {
    private fun record(stage: String = "send_invoked", id: String = "cmd1", time: Long = 1000) = """
        {"schema":"domopi.semantic_command.v1","entity_id":"lights.tv.power",
        "command_id":"$id","actor":"utente","actor_policy":"dashboard_only_v2",
        "origin":"app_interface","attribution":"interface_contract","requested_state":true,
        "stage":"$stage","observed_at_ms":$time,"execution_confirmed":false,
        "physical_confirmation":false,"publisher_authenticated":false}
    """.trimIndent()
    private val receipt = "semantic/v1/rm_receipts/lights.tv.power/stat"

    @Test fun semanticEvidenceDoesNotChangeDeclaredState() {
        val state = AiSmartState().observe("lights/tv/power/stat", "OFF", 500, false)
            .observe(receipt, record(), 2000, true)
        val command = state.lightCommands.getValue("lights_tv")
        assertEquals("utente", command.actor)
        assertEquals("send_invoked", command.stage)
        assertTrue(command.retained)
        assertEquals("false", state.validLightObservations().getValue("lights_tv").payload)
        assertTrue(command.toJson().contains("\"physical_confirmation\":false"))
    }
    @Test fun oldRetainedAndEarlierStageCannotDowngradeEvidence() {
        val state = AiSmartState().observe(receipt, record(), 2000, false)
        assertEquals(state, state.observe(receipt, record(id="old", time=500), 3000, true))
        assertEquals(state, state.observe("semantic/v1/routed_commands/lights.tv.power/stat",
            record(stage="routed", time=900), 3000, true))
        assertEquals("new", state.observe(receipt, record(id="new", time=3000), 4000, false)
            .lightCommands.getValue("lights_tv").commandId)
    }
    @Test fun missingPolicySpoofedTopicsAndInvalidTypesAreRejected() {
        val invalid = listOf(record().replace("dashboard_only_v2", "legacy"),
            record().replace("app_interface", "alexa"),
            record().replace("lights.tv.power", "lights.libreria.power"),
            record().replace("\"requested_state\":true", "\"requested_state\":\"true\""),
            record().replace("\"physical_confirmation\":false", "\"physical_confirmation\":true"),
            record().replace("\"observed_at_ms\":1000", "\"observed_at_ms\":true"),
            "not json")
        invalid.forEach { assertTrue(AiSmartState().observe(receipt, it, 2000, false).lightCommands.isEmpty()) }
        assertTrue(AiSmartState().observe(receipt, record(), 2000, false, "untrusted/topic").lightCommands.isEmpty())
    }
    @Test fun automationAndEvidenceWithoutStateAreAvailable() {
        val state = AiSmartState().observe(receipt, record().replace("utente", "automazione")
            .replace("app_interface", "alexa"), 2000, false)
        assertEquals("automazione", state.lightCommands.getValue("lights_tv").actor)
        assertTrue(state.validLightObservations().isEmpty())
    }
}
