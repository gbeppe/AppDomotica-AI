package com.domopi.app.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.domopi.app.ui.theme.DomoPiTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in integration check against a read-only service supplied by the runner. */
class HouseAiScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dailyReportAndErrors() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires an explicitly configured read-only integration service",
            args.getString("houseAiUrl") != null && args.getString("houseAiToken") != null)
        val url = requireNotNull(args.getString("houseAiUrl"))
        val token = requireNotNull(args.getString("houseAiToken"))
        compose.setContent { DomoPiTheme(darkTheme = args.getString("houseAiDark") == "true") { HouseAiScreen(onBack = {}) } }
        compose.onNodeWithText("Analizza la giornata").assertIsNotEnabled()
        compose.onNodeWithText("Indirizzo del servizio").performTextInput(url)
        compose.onNodeWithText("Token di accesso").performTextInput(token)
        compose.onNodeWithText("Giorno (AAAA-MM-GG)").performTextReplacement("2026-09-06")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Analizza la giornata").performClick()
        compose.waitUntil(120_000) {
            compose.onAllNodesWithText("2026-09-06 · Europe/Rome").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("2026-09-06 · Europe/Rome").performScrollTo().assertIsDisplayed()
        screenshot("house-ai-report.png")
        compose.onNodeWithText("Eventi registrati: 62").performScrollTo().assertIsDisplayed()
        screenshot("house-ai-events.png")
        compose.onNodeWithText("PEAK_SHAVING_ATTIVO (Scarica: 3190W)").performScrollTo().assertIsDisplayed()
        screenshot("house-ai-event-detail.png")
        compose.onNodeWithText("Giorno (AAAA-MM-GG)").performScrollTo().performTextReplacement("invalid")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Analizza la giornata").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Impossibile leggere il rapporto. Verifica data, indirizzo, accesso e connessione.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Impossibile leggere il rapporto. Verifica data, indirizzo, accesso e connessione.")
            .performScrollTo().assertIsDisplayed()
        screenshot("house-ai-error.png")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(700) // Allow the rendered frame to reach the screenshot surface.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
