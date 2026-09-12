package com.domopi.app.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.domopi.app.data.AiSmartState
import com.domopi.app.ui.theme.DomoPiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated content: does not start MainActivity, MQTT, speech services or the historical API. */
class AiSmartScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun state(): AiSmartState {
        var result = AiSmartState()
        AiSmartState.lightTopics.forEachIndexed { i, topic ->
            if (i != 5) result = result.observe(topic, if (i == 4) "false" else "true", 1789154400000, true)
        }
        return result.observe(AiSmartState.livingTopic, "24.2", 1789154400000, true)
            .observe(AiSmartState.acsTopic, "41.1", 1789154430000, false)
    }

    @Test fun compoundAnswerMatchesSpokenTextAndChangesOnDisconnection() {
        var connected by mutableStateOf(true)
        var spoken = ""
        compose.setContent {
            var question by remember { mutableStateOf("") }
            DomoPiTheme(darkTheme = true) {
                AiSmartContent(state(), connected, question, { question = it }, {}, {},
                    speechReady = true, onSpeak = { spoken = it })
            }
        }
        compose.onNodeWithText("Chiedi", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithText("La tua domanda").performScrollTo()
            .performTextInput("Quante luci sono accese e quali sono le temperature living e ACS?")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Chiedi").performScrollTo().performClick()
        compose.onNodeWithTag("smart-answer").performScrollTo()
            .assertTextContains("6 punti luce accesi", substring = true)
            .assertTextContains("24,2 gradi", substring = true)
            .assertTextContains("41,1 gradi", substring = true)
        screenshot("smart-dark-answer.png")
        compose.onNodeWithText("Leggi risposta").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(state().answer("Quante luci sono accese e quali sono le temperature living e ACS?", true), spoken)
            connected = false
        }
        compose.onNodeWithTag("smart-answer").performScrollTo()
            .assertTextContains("Connessione assente", substring = true)
        screenshot("smart-dark-disconnected.png")
    }

    @Test fun provenanceShowsUnknownHifiAndActualSourceInLightTheme() {
        compose.setContent {
            DomoPiTheme(darkTheme = false) { AiSmartContent(state(), true, "", {}, {}, {}) }
        }
        compose.onNodeWithTag("smart-summary").assertTextContains("1 senza un dato valido", substring = true)
        screenshot("smart-light-summary.png")
        compose.onNodeWithText("Mostra provenienza e limiti").performScrollTo().performClick()
        compose.onNodeWithTag("source-lights/hifi/power/stat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dato non ricevuto").assertIsDisplayed()
        screenshot("smart-light-provenance.png")
        compose.onNodeWithText("zara/interface/energy/puffer_acs/stat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Messaggio non retained.", substring = true).assertIsDisplayed()
    }

    @Test fun missingDataAndVoiceUnavailableKeepTextAndNavigationUsable() {
        var classic = false
        var history = false
        compose.setContent {
            var question by remember { mutableStateOf("Temperatura living e ACS?") }
            DomoPiTheme(darkTheme = true) {
                AiSmartContent(AiSmartState(), false, question, { question = it },
                    { classic = true }, { history = true }, voiceNotice = "Riconoscimento non disponibile")
            }
        }
        compose.onNodeWithText("Leggi riepilogo").assertIsNotEnabled()
        compose.onNodeWithText("Chiedi").performScrollTo().performClick()
        compose.onNodeWithTag("smart-answer").performScrollTo()
            .assertTextContains("Temperatura living: dato non disponibile", substring = true)
            .assertTextContains("Acqua sanitaria ACS: dato non disponibile", substring = true)
        compose.onNodeWithText("Leggi risposta").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Storico e motivazioni").performScrollTo().performClick()
        compose.onNodeWithText("Apri app classica").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(classic); assertTrue(history) }
        screenshot("smart-dark-missing.png")
    }

    @Test fun dictationCanBeCorrectedBeforeAskingAndReadingCanBeStopped() {
        var stopped = false
        compose.setContent {
            var question by remember { mutableStateOf("") }
            DomoPiTheme {
                AiSmartContent(state(), true, question, { question = it }, {}, {}, speechReady = true,
                    onDictate = { question = "Temperatura camera" }, onStopSpeaking = { stopped = true })
            }
        }
        compose.onNodeWithText("Detta domanda").performScrollTo().performClick()
        compose.onNodeWithTag("smart-answer").assertDoesNotExist()
        compose.onNodeWithText("La tua domanda").performScrollTo().performTextReplacement("Temperatura living e ACS")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Chiedi").performScrollTo().performClick()
        compose.onNodeWithTag("smart-answer").performScrollTo().assertTextContains("41,1 gradi", substring = true)
        compose.onNodeWithText("Ferma lettura").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(stopped) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
