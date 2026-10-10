package com.domopi.app.data

/** Average and busiest second over the meter window, in live (non-retained) messages. */
data class MessageRate(val averagePerSecond: Int = 0, val peakPerSecond: Int = 0)

/**
 * Sliding-window MQTT rate. Messages are counted in one-second buckets and the
 * snapshot is computed on a clock tick, so silence lowers the rate instead of
 * freezing the last burst. Retained replays are not passed in by the caller.
 */
class MessageRateMeter(private val windowSeconds: Int = 10) {
    private val buckets = LongArray(windowSeconds)
    private val bucketSecond = LongArray(windowSeconds) { Long.MIN_VALUE }

    @Synchronized
    fun record(nowMs: Long) {
        val second = Math.floorDiv(nowMs, 1000L)
        val index = Math.floorMod(second, windowSeconds.toLong()).toInt()
        if (bucketSecond[index] != second) {
            bucketSecond[index] = second
            buckets[index] = 0
        }
        buckets[index]++
    }

    /** Covers the [windowSeconds] whole seconds before the one in progress. */
    @Synchronized
    fun snapshot(nowMs: Long): MessageRate {
        val current = Math.floorDiv(nowMs, 1000L)
        var total = 0L
        var peak = 0L
        for (i in 0 until windowSeconds) {
            val second = bucketSecond[i]
            if (second < current && second >= current - windowSeconds) {
                total += buckets[i]
                peak = maxOf(peak, buckets[i])
            }
        }
        return MessageRate(Math.round(total.toDouble() / windowSeconds).toInt(), peak.toInt())
    }
}
