package com.nuvio.tv.ui.screens.player

import org.junit.Assert.*
import org.junit.Test

class UniversalBitratePolicyTest {
    @Test fun adaptationNeverUpgradesPastUserCeiling() {
        val policy = ServerBitratePolicy()
        for (time in 0L..400_000L step 1_000) {
            assertNull(policy.sample("movie", time, time, 40_000, false, false,
                20_000_000, 20_000_000, 150_000_000, false, ceilingBitrate = 20_000_000))
        }
    }

    @Test fun aHigherUserCeilingAllowsRecoveryAboveTheFamilyFortyMbpsLimit() {
        val policy = ServerBitratePolicy()
        val upgrade = (0L..400_000L step 1_000).firstNotNullOfOrNull { time ->
            policy.sample("movie", time, time, 40_000, false, false,
                60_000_000, 60_000_000, 200_000_000, false, ceilingBitrate = 80_000_000)
        }
        assertEquals(80_000_000L, upgrade)
    }
}
