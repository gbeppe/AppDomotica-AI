package com.domopi.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import com.domopi.app.data.SmartControlState
import com.domopi.app.ui.theme.DomoPiTheme
import org.junit.Rule
import org.junit.Test

class SmartControlsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsRealStateButKeepsCommandControlsDisabled() {
        var state = SmartControlState().observe("ai/system_enabled/stat", "true", 10, true,
            "zara/interface/ai/system_enabled/stat")
        state = state.observe("fireplace/main/mode/stat", "Manuale", 11, false,
            "zara/interface/fireplace/main/mode/stat")
        compose.setContent {
            DomoPiTheme(darkTheme = true) { SmartControlsScreen(state, onBack = {}) }
        }
        compose.onNodeWithTag("smart-control-ai_enabled").assertIsDisplayed()
        compose.onNodeWithText("true").assertIsDisplayed()
        compose.onNodeWithTag("smart-control-input-ai_enabled").assertHasNoClickAction()
        compose.onNodeWithTag("smart-controls-list")
            .performScrollToNode(hasTestTag("smart-control-fireplace_mode"))
        compose.onNodeWithTag("smart-control-fireplace_mode").assertIsDisplayed()
        compose.onAllNodesWithText("Manuale", useUnmergedTree = true).assertCountEquals(2)
    }
}
