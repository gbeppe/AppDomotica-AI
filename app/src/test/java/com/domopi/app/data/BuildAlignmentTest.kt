package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildAlignmentTest {
    private val sha = "0123456789abcdef0123456789abcdef01234567"
    private val local = BuildIdentity(sha, "clean", "bld_1", "2026-10-10T12:00:00Z")

    private fun payload(
        schema: String = BuildAlignment.SCHEMA, component: String = "android",
        gitSha: String = sha, dirty: String = "clean", buildId: String = "bld_1",
        timestamp: String = "2026-10-10T12:00:00Z",
    ) = """{"schema":"$schema","component":"$component","git_sha":"$gitSha","dirty":"$dirty",""" +
        """"build_id":"$buildId","build_timestamp":"$timestamp"}"""

    @Test fun parsesPublishedIdentity() =
        assertEquals(PublishedBuild.Received(local), BuildAlignment.parse(payload()))

    @Test fun emptyRetainedMeansAbsent() = assertEquals(PublishedBuild.Absent, BuildAlignment.parse(""))

    @Test fun wrongSchemaComponentOrGarbageIsInvalid() {
        assertEquals(PublishedBuild.Invalid, BuildAlignment.parse(payload(schema = "wallbox.build_identity.v1")))
        assertEquals(PublishedBuild.Invalid, BuildAlignment.parse(payload(component = "core")))
        assertEquals(PublishedBuild.Invalid, BuildAlignment.parse(payload(buildId = "unknown")))
        assertEquals(PublishedBuild.Invalid, BuildAlignment.parse("""{"schema":"${BuildAlignment.SCHEMA}"}"""))
        assertEquals(PublishedBuild.Invalid, BuildAlignment.parse("not json"))
    }

    @Test fun alignedOnlyWhenEveryIdentityFieldMatches() {
        fun eval(published: PublishedBuild) = BuildAlignment.evaluate(true, true, published, local)
        assertEquals(AppAlignmentStatus.ALIGNED, eval(PublishedBuild.Received(local)))
        for (other in listOf(local.copy(gitSha = sha.reversed()), local.copy(dirty = "dirty"),
                local.copy(buildId = "bld_2"), local.copy(buildTimestamp = "2026-10-11T12:00:00Z")))
            assertEquals(AppAlignmentStatus.VERSION_MISMATCH, eval(PublishedBuild.Received(other)))
    }

    @Test fun developmentBuildIsNeverAligned() {
        val dev = local.copy(buildId = "dev_1791586554814", buildTimestamp = "1791586554814")
        assertEquals(AppAlignmentStatus.VERSION_MISMATCH,
            BuildAlignment.evaluate(true, true, PublishedBuild.Received(local), dev))
    }

    @Test fun unverifiableCasesFlashOnlyAfterStartupGrace() {
        assertEquals(AppAlignmentStatus.CHECKING, BuildAlignment.evaluate(false, false, PublishedBuild.Waiting, local))
        assertEquals(AppAlignmentStatus.BROKER_UNREACHABLE, BuildAlignment.evaluate(false, true, PublishedBuild.Waiting, local))
        // A previously received identity cannot be verified while the broker is unreachable.
        assertEquals(AppAlignmentStatus.BROKER_UNREACHABLE,
            BuildAlignment.evaluate(false, true, PublishedBuild.Received(local), local))
        assertEquals(AppAlignmentStatus.CHECKING, BuildAlignment.evaluate(true, true, PublishedBuild.Waiting, local))
        assertEquals(AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN, BuildAlignment.evaluate(true, true, PublishedBuild.Absent, local))
        assertEquals(AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN, BuildAlignment.evaluate(true, true, PublishedBuild.Invalid, local))
        assertEquals(AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN,
            BuildAlignment.evaluate(true, true, PublishedBuild.Received(local), local.copy(gitSha = "unknown")))
    }

    @Test fun toneIsGreenFixedRedFlashingRedOrNeutral() {
        assertEquals(IndicatorTone.OK, BuildAlignment.toneOf(AppAlignmentStatus.ALIGNED))
        assertEquals(IndicatorTone.ERROR, BuildAlignment.toneOf(AppAlignmentStatus.VERSION_MISMATCH))
        assertEquals(IndicatorTone.UNVERIFIABLE, BuildAlignment.toneOf(AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN))
        assertEquals(IndicatorTone.UNVERIFIABLE, BuildAlignment.toneOf(AppAlignmentStatus.BROKER_UNREACHABLE))
        assertEquals(IndicatorTone.NEUTRAL, BuildAlignment.toneOf(AppAlignmentStatus.CHECKING))
    }

    @Test fun mqttManagerRoutesIdentityTopicOutsideDigitalTwinPrefix() {
        val manager = MqttManager()
        val handle = MqttManager::class.java.getDeclaredMethod(
            "handleIncomingMessage", String::class.java, String::class.java
        ).apply { isAccessible = true }
        assertEquals(PublishedBuild.Waiting, manager.publishedBuild.value)
        handle.invoke(manager, BuildAlignment.TOPIC, payload())
        assertEquals(PublishedBuild.Received(local), manager.publishedBuild.value)
        handle.invoke(manager, BuildAlignment.TOPIC, "")
        assertEquals(PublishedBuild.Absent, manager.publishedBuild.value)
        assertTrue(!BuildAlignment.TOPIC.startsWith(manager.digitalTwinPrefix))
    }
}
