package com.domopi.app.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.domopi.app.data.AiSmartState
import com.domopi.app.data.EnergySmartState
import com.domopi.app.data.EnergyAssistantAnswer
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
        compose.onNodeWithText("Chiedi").assertIsNotEnabled()
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

    @Test fun dynamicEnergyQuestionRequiresConfigurationAndRendersBackendAnswer() {
        var asked = ""
        compose.setContent {
            var question by remember { mutableStateOf("Quanta energia ho prodotto oggi?") }
            var url by remember { mutableStateOf("") }
            var token by remember { mutableStateOf("") }
            var answer by remember { mutableStateOf<String?>(null) }
            DomoPiTheme(darkTheme = true) {
                AiSmartContent(state(), true, question, { question = it }, {}, {},
                    dynamicMode = true, serviceUrl = url, serviceToken = token,
                    onServiceUrlChange = { url = it }, onServiceTokenChange = { token = it },
                    dynamicAnswer = answer, onAskDynamic = {
                        asked = it
                        answer = "Produzione fotovoltaica: 12,50 kWh; copertura 99,0%."
                    })
            }
        }
        compose.onNodeWithText("Chiedi").assertIsNotEnabled()
        compose.onNodeWithText("Indirizzo backend AI").performScrollTo().performTextInput("http://10.0.2.2:8765")
        compose.onNodeWithText("Token backend").performScrollTo().performTextInput("token-di-test-abbastanza-lungo")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Chiedi").performScrollTo().performClick()
        compose.onNodeWithTag("smart-answer").performScrollTo()
            .assertTextContains("12,50 kWh", substring = true)
        compose.runOnIdle { assertEquals("Quanta energia ho prodotto oggi?", asked) }
        screenshot("smart-dark-energy-dynamic.png")
    }

    @Test fun energyEvidenceAndVoiceUseTheExactAnswerAndKeepClassicNavigation() {
        var spoken = ""
        var classic = false
        val response = EnergyAssistantAnswer(
            "Prelievo dalla rete: 2,50 kWh; copertura 80,0%. Stima limitata agli intervalli coperti, non totale del periodo.",
            "partial", "Prelievo di ieri?", "2026-09-12T18:00:00+02:00",
            listOf("EmonCMS · feed 305\nCopertura 80,0%; intervalli mancanti non colmati."))
        val energy = EnergySmartState().observe("energy/grid/power_raw/stat", "-450", 1789200000000,
            true, "zara/interface/energy/grid/power_raw/stat")
        compose.setContent {
            DomoPiTheme(darkTheme = false) {
                AiSmartContent(state(), false, "Prelievo di ieri?", {}, { classic = true }, {},
                    dynamicMode = true, energyState = energy, dynamicResponse = response,
                    dynamicAnswer = response.text, speechReady = true, onSpeak = { spoken = it })
            }
        }
        compose.onNodeWithTag("smart-summary").assertTextContains("Immissione rete: 450,0 W", substring = true)
        screenshot("energy-light-summary.png")
        compose.onNodeWithTag("smart-answer").performScrollTo().assertTextEquals(response.text)
        compose.onNodeWithText("Leggi risposta").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(response.text, spoken) }
        screenshot("energy-light-answer.png")
        compose.onNodeWithText("Mostra provenienza e limiti").performScrollTo().performClick()
        compose.onNodeWithTag("energy-evidence-0").performScrollTo().assertIsDisplayed()
        screenshot("energy-light-evidence.png")
        compose.onNodeWithTag("energy-source-grid_power_w").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apri app classica").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(classic) }
    }

    @Test fun energyDictationRequiresExplicitSendAndClarificationKeepsQuestionEditable() {
        var asked = ""
        compose.setContent {
            var question by remember { mutableStateOf("") }
            var response by remember { mutableStateOf<String?>(null) }
            DomoPiTheme(darkTheme = true) {
                AiSmartContent(state(), true, question, { question = it }, {}, {},
                    dynamicMode = true, serviceUrl = "http://localhost", serviceToken = "fixture",
                    onDictate = { question = "Percentuale media di ricarica ultima settimana" },
                    dynamicAnswer = response, onAskDynamic = {
                        asked = it
                        response = "Intendi il livello medio della batteria? Quale periodo di calendario?"
                    })
            }
        }
        compose.onNodeWithText("Detta domanda").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("", asked) }
        compose.onNodeWithTag("smart-answer").assertDoesNotExist()
        compose.onNodeWithText("Chiedi").performScrollTo().performClick()
        compose.onNodeWithTag("smart-answer").performScrollTo().assertTextContains("Intendi il livello medio", substring = true)
        screenshot("energy-dark-clarification.png")
        compose.onNodeWithText("La tua domanda").performScrollTo().performTextReplacement("SOC medio dal 1 al 7 settembre inclusi")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Chiedi").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("SOC medio dal 1 al 7 settembre inclusi", asked) }
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
