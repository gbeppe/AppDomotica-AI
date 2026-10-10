package com.domopi.app.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Architecture guards for the WallBox-style top bar (UI-TOPBAR.D1). */
class TopBarIndicatorsSourceTest {
    private fun appRoot() = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun source(relative: String) = File(appRoot(), "src/main/java/com/domopi/app/$relative").readText()

    @Test fun topBarShowsAccessPathAndBuildIndicators() {
        val dashboard = source("ui/screens/MainDashboard.kt")
        assertTrue(dashboard.contains("AccessPathIndicator("))
        assertTrue(dashboard.contains("BuildAlignmentIcon("))
        assertFalse(dashboard.contains("Github"))
    }

    @Test fun accessPathIsDisplayOnlyAndNeverSelectsEndpoints() {
        val activity = source("MainActivity.kt")
        val mqttWiring = activity.substringAfter("combine(").substringBefore("// Topbar:")
        listOf("AccessPath", "accessPath", "NetworkEvidence").forEach {
            assertFalse("MQTT endpoint must not depend on $it", mqttWiring.contains(it))
        }
    }

    @Test fun noGithubApiCallRemains() {
        val root = File(appRoot(), "src/main/java")
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("api.github.com") }.map { it.name }.toList()
        assertTrue("GitHub API still used in $offenders", offenders.isEmpty())
    }

    @Test fun gradleBuildsNeverPublishTheIdentity() {
        val gradle = File(appRoot(), "build.gradle.kts").readText()
        assertTrue(gradle.contains("domopi.release.gitSha"))
        assertTrue(gradle.contains("Incomplete DomoPi release identity"))
        listOf("mosquitto", "publish_android_build", "Exec>").forEach {
            assertFalse("Gradle must not publish ($it)", gradle.contains(it))
        }
    }
}
