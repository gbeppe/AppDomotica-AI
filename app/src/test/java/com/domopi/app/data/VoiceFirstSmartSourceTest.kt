package com.domopi.app.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voice-first AI Smart: a recognized question is sent at once and only spoken questions are read aloud. */
class VoiceFirstSmartSourceTest {
    private val source = listOf(File("src/main/java/com/domopi/app/ui/screens/AiSmartScreen.kt"),
        File("app/src/main/java/com/domopi/app/ui/screens/AiSmartScreen.kt")).first { it.exists() }.readText()

    @Test fun recognizedSpeechIsSentWithoutSecondTap() {
        assertTrue(source.contains("ask(text, spoken = true)"))
        assertFalse(source.contains("premi Chiedi"))
    }

    @Test fun onlySpokenQuestionsAreReadAutomatically() {
        assertTrue(source.contains("onAskDynamic = { ask(it, spoken = false) }"))
        assertTrue(source.contains("if (spoken && speechReady) (response?.text ?: dynamicError)?.let(::speak)"))
    }

    @Test fun speakIsThePrimaryButtonAndQuestionIsEchoed() {
        assertTrue(source.contains("Text(\"Parla\""))
        assertTrue(source.contains("Hai chiesto: \$asked"))
    }
}
