package com.nuvio.tv.ui.screens.player

import org.junit.Assert.*
import org.junit.Test

class FamilyServerBitratePolicyTest {
    private fun ServerBitratePolicy.tick(
        now: Long, cap: Long = 40_000_000, buffer: Long = 30_000, speed: Long? = 70_000_000,
        buffering: Boolean = false, paused: Boolean = false, position: Long = now,
        stamp: Long = now, slow: Boolean = false, key: String = "movie"
    ) = sample(key, now, position, buffer, buffering, paused, cap, cap, speed, slow, stamp)

    @Test fun reactsBeforeTheFirstStallWithoutTheOldMinuteDelay() {
        val p = ServerBitratePolicy(true)
        p.tick(0)
        for (t in 1_000L..7_000 step 1_000) assertNull(p.tick(t, buffer = 8_000, speed = 32_000_000))
        assertEquals(35_000_000L, p.tick(8_000, buffer = 8_000, speed = 32_000_000))
    }

    @Test fun confirmedStallCanSkipOneRungButNotDropToTheLowestQuality() {
        val p = ServerBitratePolicy(true)
        for (t in 0L..10_000 step 1_000) p.tick(t)
        assertNull(p.tick(11_000, buffer = 0, speed = 20_000_000, buffering = true))
        assertNull(p.tick(12_000, buffer = 0, speed = 20_000_000, buffering = true))
        assertEquals(30_000_000L, p.tick(13_000, buffer = 0, speed = 20_000_000, buffering = true))
    }

    @Test fun healthyBufferNeverDowngradesEvenWhenObservedTransferIsLow() {
        val p = ServerBitratePolicy(true)
        for (t in 0L..300_000 step 1_000) assertNull(p.tick(t, speed = 10_000_000))
    }

    @Test fun stallsWithoutThroughputOrSlowLoadEvidenceDoNotTriggerTranscoding() {
        val p = ServerBitratePolicy(true)
        for (t in 0L..120_000 step 1_000) assertNull(p.tick(t, speed = null, buffer = 0, buffering = true))
        assertNull(p.tick(121_000, speed = null, buffer = 0, slow = true))
        assertEquals(35_000_000L, p.tick(125_000, speed = null, buffer = 0, slow = true))
    }

    @Test fun stableWindowSurvivesIdleDownloadsAndNormalBufferOscillation() {
        val p = ServerBitratePolicy(true)
        p.tick(0, cap = 35_000_000)
        for (t in 1_000L..179_000 step 1_000) {
            val active = t % 30_000 < 5_000
            assertNull(p.tick(t, cap = 35_000_000, buffer = if (active) 19_000 else 30_000,
                speed = if (active) 70_000_000 else null))
        }
        assertEquals(40_000_000L, p.tick(180_000, cap = 35_000_000))
    }

    @Test fun oneStaleHighSampleCannotProveRecovery() {
        val p = ServerBitratePolicy(true)
        for (t in 0L..300_000 step 1_000) assertNull(p.tick(t, cap = 35_000_000, stamp = 0))
        assertNull(p.tick(301_000, cap = 35_000_000))
        assertNull(p.tick(302_000, cap = 35_000_000))
        assertEquals(40_000_000L, p.tick(303_000, cap = 35_000_000))
    }

    @Test fun recentInsufficientTransferDelaysUpgradeButDoesNotEraseAllStability() {
        val p = ServerBitratePolicy(true)
        for (t in 0L..175_000 step 1_000) assertNull(p.tick(t, cap = 35_000_000))
        assertNull(p.tick(176_000, cap = 35_000_000, speed = 40_000_000))
        for (t in 177_000L..205_000 step 1_000) assertNull(p.tick(t, cap = 35_000_000))
        assertEquals(40_000_000L, p.tick(206_000, cap = 35_000_000))
    }

    @Test fun failedUpgradeIsNotRepeatedForTenMinutes() {
        val p = ServerBitratePolicy(true)
        p.tick(0, cap = 35_000_000)
        p.changed(200_000, 35_000_000, 40_000_000)
        p.changed(220_000, 40_000_000, 35_000_000)
        for (t in 221_000L..819_000 step 1_000) assertNull(p.tick(t, cap = 35_000_000))
        assertEquals(40_000_000L, p.tick(820_000, cap = 35_000_000))
    }

    @Test fun downgradesHaveCooldownAndFailedNegotiationHasLongerBackoff() {
        val p = ServerBitratePolicy(true)
        p.tick(0)
        p.changed(10_000, 40_000_000, 35_000_000)
        for (t in 11_000L..29_000 step 1_000) assertNull(p.tick(t, cap = 35_000_000, buffer = 2_000, speed = 25_000_000))
        assertEquals(30_000_000L, p.tick(30_000, cap = 35_000_000, buffer = 2_000, speed = 25_000_000))
        p.failed(30_000)
        for (t in 31_000L..89_000 step 1_000) assertNull(p.tick(t, buffer = 0, speed = 20_000_000, buffering = true))
    }

    @Test fun pauseAndSeekClearPressureAndStability() {
        val p = ServerBitratePolicy(true)
        for (t in 0L..179_000 step 1_000) assertNull(p.tick(t, cap = 35_000_000))
        assertNull(p.tick(180_000, cap = 35_000_000, paused = true))
        assertNull(p.tick(181_000, cap = 35_000_000, position = 900_000))
        assertNull(p.tick(182_000, cap = 35_000_000, position = 901_000))
    }
}
