package com.domopi.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Build identity published (retained) by tools/release_android.py on
 * [BuildAlignment.TOPIC]. Same contract as WallBox REL-APP.1, schema renamed.
 */
data class BuildIdentity(
    val gitSha: String,
    val dirty: String,
    val buildId: String,
    val buildTimestamp: String,
)

/** What the broker told us about the published identity since the last connection. */
sealed class PublishedBuild {
    /** Subscribed, retained replay not yet received. */
    object Waiting : PublishedBuild()
    /** No retained identity on the broker. */
    object Absent : PublishedBuild()
    /** A retained payload exists but does not match the schema. */
    object Invalid : PublishedBuild()
    data class Received(val identity: BuildIdentity) : PublishedBuild()
}

enum class AppAlignmentStatus {
    /** First evaluation still pending: neutral, never an alarm. */
    CHECKING,
    ALIGNED,
    VERSION_MISMATCH,
    PUBLISHED_BUILD_UNKNOWN,
    BROKER_UNREACHABLE,
}

enum class IndicatorTone { NEUTRAL, OK, ERROR, UNVERIFIABLE }

object BuildAlignment {
    const val TOPIC = "domopi/build/android"
    const val SCHEMA = "domopi.build_identity.v1"

    /** Grace period after app start before an unreachable broker turns the icon red. */
    const val STARTUP_GRACE_MS = 15_000L

    /** Time allowed for the retained replay after (re)subscription. */
    const val RETAINED_WAIT_MS = 3_000L

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): PublishedBuild {
        if (payload.isBlank()) return PublishedBuild.Absent
        return try {
            val obj: JsonObject = json.parseToJsonElement(payload).jsonObject
            fun field(name: String) = obj[name]?.jsonPrimitive?.contentOrNull
            if (field("schema") != SCHEMA || field("component") != "android") return PublishedBuild.Invalid
            val identity = BuildIdentity(
                gitSha = field("git_sha") ?: return PublishedBuild.Invalid,
                dirty = field("dirty") ?: return PublishedBuild.Invalid,
                buildId = field("build_id") ?: return PublishedBuild.Invalid,
                buildTimestamp = field("build_timestamp") ?: return PublishedBuild.Invalid,
            )
            if (!isComplete(identity)) PublishedBuild.Invalid else PublishedBuild.Received(identity)
        } catch (_: Exception) {
            PublishedBuild.Invalid
        }
    }

    private fun isComplete(identity: BuildIdentity) =
        listOf(identity.gitSha, identity.dirty, identity.buildId, identity.buildTimestamp)
            .none { it.isBlank() || it == "unknown" }

    fun evaluate(
        brokerConnected: Boolean,
        startupGraceOver: Boolean,
        published: PublishedBuild,
        local: BuildIdentity,
    ): AppAlignmentStatus {
        if (!brokerConnected) {
            return if (startupGraceOver) AppAlignmentStatus.BROKER_UNREACHABLE else AppAlignmentStatus.CHECKING
        }
        return when (published) {
            PublishedBuild.Waiting -> AppAlignmentStatus.CHECKING
            PublishedBuild.Absent, PublishedBuild.Invalid -> AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN
            is PublishedBuild.Received ->
                if (!isComplete(local)) AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN
                else if (published.identity == local) AppAlignmentStatus.ALIGNED
                else AppAlignmentStatus.VERSION_MISMATCH
        }
    }

    fun toneOf(status: AppAlignmentStatus): IndicatorTone = when (status) {
        AppAlignmentStatus.CHECKING -> IndicatorTone.NEUTRAL
        AppAlignmentStatus.ALIGNED -> IndicatorTone.OK
        AppAlignmentStatus.VERSION_MISMATCH -> IndicatorTone.ERROR
        AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN,
        AppAlignmentStatus.BROKER_UNREACHABLE -> IndicatorTone.UNVERIFIABLE
    }

    fun describe(status: AppAlignmentStatus): String = when (status) {
        AppAlignmentStatus.CHECKING -> "Verifica in corso"
        AppAlignmentStatus.ALIGNED -> "Allineata all'ultima release pubblicata"
        AppAlignmentStatus.VERSION_MISMATCH -> "Non allineata: l'app installata è diversa dall'ultima release pubblicata"
        AppAlignmentStatus.PUBLISHED_BUILD_UNKNOWN -> "Impossibile verificare: identità della release pubblicata non disponibile"
        AppAlignmentStatus.BROKER_UNREACHABLE -> "Impossibile verificare: broker MQTT non raggiungibile. Vedi diagnostica"
    }
}
