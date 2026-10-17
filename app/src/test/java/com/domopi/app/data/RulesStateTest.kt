package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesStateTest {
    // As published by RULES-ENGINE.1 on zara/interface/rules/list/stat.
    private val list = """{"schema":"domopi.rules.v1","engine_mode":"shadow","max_active":10,"rules":[{"id":"r-puffer-42",
        "text":"quando il puffer alto supera 42 °C spegni il caminetto","status":"test","armed":true,
        "test_until":"2026-10-17T16:40:46.852Z","last_fired":null,"trigger":{"kind":"threshold","metric":"puffer_top","op":"above","value":42},
        "conditions":[],"action":{"tool":"set_fireplace_power","state":"off"},"lifetime":{"kind":"once"}},
        {"id":"BAD ID","text":"x","status":"test","action":{"tool":"set_light_scene"}}]}"""

    @Test fun parsesTheEngineListAndDropsInvalidRules() {
        val state = RulesState.parse(list)!!
        assertEquals("shadow", state.engineMode)
        assertEquals(listOf(RuleSummary("r-puffer-42", "quando il puffer alto supera 42 °C spegni il caminetto", "test",
            "set_fireplace_power")), state.rules)
        assertTrue(state.known)
        assertNull(RulesState.parse("""{"engine_mode":"on","rules":[]}"""))
        assertFalse(RulesState().known)
    }

    @Test fun ruleActionsArePublishedOnlyForCreateAndDelete() {
        val manager = MqttManager()
        assertTrue(manager.publishRuleAction(RuleAction("create", """{"text":"x"}""")))
        assertTrue(manager.publishRuleAction(RuleAction("delete", "r-puffer-42")))
        assertFalse(manager.publishRuleAction(RuleAction("engine", "active")))
        assertFalse(manager.publishRuleAction(RuleAction("delete", "")))
    }
}
