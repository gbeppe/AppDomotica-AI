package com.domopi.app.data

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository is public: no password, PIN or API key may appear in the app sources.
 * Former values are checked by SHA-256 so this test does not publish them again.
 */
class NoSecretsInCodeTest {
    private val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
    private val sources = root.walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

    // SHA-256 of the former EmonCMS read key and MQTT default password.
    private val formerSecrets = setOf(
        "749b56bfc0ffe43c4fde54cbd7781184e6a3b80ca793e001739d1258c1559dd9",
        "4722df493a667bdbd89ea9f1fd7819ad8702ffd644c5d616195a36c8fa72a869",
    )

    @Test fun noHexApiKeyLiteral() {
        val hexKey = Regex("\"[0-9a-f]{32}\"")
        assertFalse(sources.any { hexKey.containsMatchIn(it) })
    }

    @Test fun noSecretDefaultsInSettings() {
        val settings = File(root, "com/domopi/app/data/SettingsManager.kt").readText()
        for (forbidden in listOf("?: \"1234\"", "?: \"password\"")) assertFalse(forbidden, settings.contains(forbidden))
        assertTrue(settings.contains("val domopiPass: Flow<String> = secret(Credential.MQTT_PASSWORD)"))
        assertTrue(settings.contains("val emoncmsApiKey: Flow<String> = secret(Credential.EMONCMS_API_KEY)"))
    }

    @Test fun formerSecretsAreGoneByHash() {
        val literal = Regex("\"([^\"\\\\]{6,64})\"")
        val hashes = sources.flatMap { src -> literal.findAll(src).map { sha256(it.groupValues[1]) }.toList() }.toSet()
        assertFalse((hashes intersect formerSecrets).isNotEmpty())
    }

    @Test fun mqttSendsCredentialsOnlyAsAPair() {
        val mqtt = File(root, "com/domopi/app/data/MqttManager.kt").readText()
        assertTrue(mqtt.contains("if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {"))
    }
}

/** Every secret field uses SecretTextField (masked, explicit paste that clears the clipboard). */
class SecretFieldsTest {
    private val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
    private fun screen(name: String) = File(root, "com/domopi/app/ui/screens/$name").readText()

    @Test fun secretsUseTheSecretField() {
        val config = screen("ConfigurationScreen.kt")
        for (tag in listOf("config-mqtt-password", "config-emoncms-key", "config-tinycam-password"))
            assertTrue(tag, config.contains("testTag = \"$tag\""))
        assertTrue(screen("AiSmartScreen.kt").contains("testTag = \"smart-token\""))
        assertTrue(screen("HouseAiScreen.kt").contains("testTag = \"house-ai-token\""))
    }

    @Test fun pasteClearsTheClipboard() {
        val field = File(root, "com/domopi/app/ui/components/SecretTextField.kt").readText()
        assertTrue(field.contains("clipboard.clearPrimaryClip()"))
        assertTrue(field.contains("PasswordVisualTransformation()"))
    }
}
