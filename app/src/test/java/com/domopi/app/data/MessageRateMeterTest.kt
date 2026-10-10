package com.domopi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRateMeterTest {
    private val t0 = 1_000_000_000L

    private fun MessageRateMeter.burst(second: Int, count: Int) =
        repeat(count) { record(t0 + second * 1000L + it) }

    @Test fun emptyMeterIsZero() = assertEquals(MessageRate(0, 0), MessageRateMeter().snapshot(t0))

    @Test fun burstyTrafficMeasuredOn20261010GivesTheRealAverage() {
        // Per-second counts observed on .20 for the app subscription set (30 s, 521 msgs).
        val observed = listOf(47, 1, 2, 45, 1, 0, 0, 0, 1, 0, 0, 3, 45, 0, 0, 0, 0, 93, 91, 0)
        val meter = MessageRateMeter(10)
        observed.forEachIndexed { second, count -> meter.burst(second, count) }
        // Window = seconds 10..19: 0+3+45+0+0+0+0+93+91+0 = 232 msgs -> 23/s, busiest second 93.
        assertEquals(MessageRate(23, 93), meter.snapshot(t0 + 20_000))
    }

    @Test fun silenceDecaysInsteadOfFreezingTheLastBurst() {
        val meter = MessageRateMeter(10)
        meter.burst(0, 142)
        assertEquals(MessageRate(14, 142), meter.snapshot(t0 + 1_000))
        assertEquals(MessageRate(14, 142), meter.snapshot(t0 + 10_999))
        assertEquals(MessageRate(0, 0), meter.snapshot(t0 + 11_000))
    }

    @Test fun secondInProgressIsNotCountedYet() {
        val meter = MessageRateMeter(10)
        meter.burst(5, 50)
        assertEquals(MessageRate(0, 0), meter.snapshot(t0 + 5_900))
        assertEquals(MessageRate(5, 50), meter.snapshot(t0 + 6_000))
    }

    @Test fun reusedBucketsAreResetAfterWrapAround() {
        val meter = MessageRateMeter(10)
        meter.burst(0, 100)
        meter.burst(10, 7) // same bucket index as second 0
        assertEquals(MessageRate(1, 7), meter.snapshot(t0 + 11_000))
    }

    @Test fun trafficLogKeepsFiftyNewestEntriesWithoutDroppingBursts() {
        val manager = MqttManager()
        val add = MqttManager::class.java.getDeclaredMethod("addTrafficLog", String::class.java)
            .apply { isAccessible = true }
        repeat(60) { add.invoke(manager, "msg $it") }
        val log = manager.trafficLog.value
        assertEquals(50, log.size)
        assertTrue(log.first().endsWith("msg 59"))
        assertTrue(log.last().endsWith("msg 10"))
    }
}
