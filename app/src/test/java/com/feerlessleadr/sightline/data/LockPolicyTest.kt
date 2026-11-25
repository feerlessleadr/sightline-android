package com.feerlessleadr.sightline.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    private val minute = 60_000L

    @Test fun `always asks every time`() {
        assertFalse(LockPolicy.stillUnlocked(LockAfter.Always, lastStopMillis = 1_000, nowMillis = 1_001))
    }

    @Test fun `within the allowed time the prompt is skipped`() {
        assertTrue(LockPolicy.stillUnlocked(LockAfter.Minutes15, 10 * minute, 10 * minute + 14 * minute))
        assertTrue(LockPolicy.stillUnlocked(LockAfter.Minutes15, 10 * minute, 10 * minute + 15 * minute))
    }

    @Test fun `after the allowed time it asks`() {
        assertFalse(LockPolicy.stillUnlocked(LockAfter.Minutes5, 10 * minute, 10 * minute + 5 * minute + 1))
    }

    @Test fun `never always skips the prompt`() {
        assertTrue(LockPolicy.stillUnlocked(LockAfter.Never, 0, 5 * minute))
        assertTrue(LockPolicy.stillUnlocked(LockAfter.Never, 1, 10_000 * minute))
    }

    @Test fun `no record of leaving, or a clock set back, counts as locked`() {
        assertFalse(LockPolicy.stillUnlocked(LockAfter.Minutes60, 0, 5 * minute))
        assertFalse(LockPolicy.stillUnlocked(LockAfter.Minutes60, 10 * minute, 5 * minute))
    }
}
