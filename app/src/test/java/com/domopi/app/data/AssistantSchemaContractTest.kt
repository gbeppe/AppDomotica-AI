package com.domopi.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every result schema an assistant tool can return must be parsed by the app; an unknown
 * schema makes the whole answer fail with "Schema evidenza non riconosciuto".
 */
class AssistantSchemaContractTest {
    private fun repoRoot() = listOf(File(".."), File(".")).first { File(it, "backend/house_ai").isDirectory }

    @Test fun appParsesEveryToolResultSchema() {
        val backend = File(repoRoot(), "backend/house_ai")
        val toolFiles = listOf("energy_tools.py", "energy_history.py", "log_tools.py")
        val produced = toolFiles.flatMap { name ->
            Regex("""['"]schema['"]\s*:\s*['"](house_ai\.[a-z_]+\.v1)['"]""")
                .findAll(File(backend, name).readText()).map { it.groupValues[1] }.toList()
        }.toSet() - setOf("house_ai.planner_request.v1")
        assertTrue("no schemas found: check the regex", produced.size >= 8)
        val parser = File(repoRoot(), "app/src/main/java/com/domopi/app/data/EnergyAssistantAnswer.kt").readText()
        val missing = produced.filterNot { parser.contains("\"$it\"") }
        assertTrue("App does not parse: $missing", missing.isEmpty())
    }
}
