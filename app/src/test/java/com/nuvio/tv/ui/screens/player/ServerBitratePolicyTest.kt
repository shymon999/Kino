package com.nuvio.tv.ui.screens.player

import org.junit.Assert.*
import org.junit.Test

class ServerBitratePolicyTest {
    private fun ServerBitratePolicy.tick(now: Long, bitrate: Long = 40_000_000, buffer: Long = 3_000,
        speed: Long? = 25_000_000, buffering: Boolean = false, paused: Boolean = false,
        required: Long? = 40_000_000, position: Long = now): Long? =
        sample("movie", now, position, buffer, buffering, paused, bitrate, required, speed, false)

    @Test fun needsSustainedBufferPressureAndEvidenceOfInsufficientThroughput() {
        val policy = ServerBitratePolicy()
        assertNull(policy.tick(0))
        for (t in 1_000L..60_000L step 1_000) assertNull(policy.tick(t, buffer = 40_000))
        for (t in 61_000L..68_000L step 1_000) assertNull(policy.tick(t))
        assertEquals(35_000_000L, policy.tick(69_000))
    }

    @Test fun lowDownloadRateAloneNeverLowersQuality() {
        val policy = ServerBitratePolicy()
        for (t in 0L..300_000L step 1_000) assertNull(policy.tick(t, buffer = 35_000, speed = 15_000_000))
    }

    @Test fun pauseSeekAndUnknownBandwidthDoNotTriggerDowngrade() {
        for (mode in 0..2) {
            val policy = ServerBitratePolicy()
            for (t in 0L..200_000L step 1_000) {
                assertNull(policy.tick(t, paused = mode == 0, speed = if (mode == 1) null else 1_000_000,
                    position = if (mode == 2) t * 10 else t))
            }
        }
    }

    @Test fun upgradeRequiresThreeMinutesOfHighBufferAndHeadroom() {
        val policy = ServerBitratePolicy()
        policy.tick(0, bitrate = 30_000_000)
        for (t in 1_000L..239_000L step 1_000) assertNull(policy.tick(t, bitrate = 30_000_000, buffer = 30_000, speed = 60_000_000))
        assertEquals(35_000_000L, policy.tick(240_000, bitrate = 30_000_000, buffer = 30_000, speed = 60_000_000))
        policy.changed(240_000, 30_000_000, 35_000_000)
        for (t in 241_000L..299_000L step 1_000) assertNull(policy.tick(t, bitrate = 35_000_000))
    }

    @Test fun failedNegotiationBacksOffInsteadOfRetryingEveryTick() {
        val policy = ServerBitratePolicy()
        policy.tick(0)
        policy.failed(100_000)
        for (t in 101_000L..159_000L step 1_000) assertNull(policy.tick(t))
    }
}
