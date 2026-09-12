package com.domopi.app.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.domopi.app.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Run only inside the network-isolated emulator harness, never against household services. */
class EnergyNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun dashboardSmartHistoryAndClassicRoutesWorkWithoutHouseholdNetwork() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("houseAiIsolated") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("AI smart").performClick()
            compose.onNodeWithText("Energia della casa").assertIsDisplayed()
            compose.onNodeWithText("Storico e motivazioni").performScrollTo().performClick()
            compose.onNodeWithText("Analizza la giornata").assertExists()
            compose.onNodeWithText("Indietro").performClick()
            compose.onNodeWithText("Energia della casa").assertIsDisplayed()
            compose.onNodeWithText("Apri app classica").performScrollTo().performClick()
            compose.onNodeWithText("AI smart").assertIsDisplayed()
        }
    }
}
