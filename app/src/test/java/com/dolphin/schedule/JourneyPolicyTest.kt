package com.dolphin.schedule

import org.junit.Assert.*
import org.junit.Test

class JourneyPolicyTest {
    @Test fun durationNeverExceedsHalfHourAndKeepsMinimumUsableWindow() {
        assertEquals(60_000L,JourneyPolicy.duration(Long.MIN_VALUE))
        assertEquals(60_000L,JourneyPolicy.duration(0))
        assertEquals(90_000L,JourneyPolicy.duration(90_000))
        assertEquals(1_800_000L,JourneyPolicy.duration(Long.MAX_VALUE))
    }
    @Test fun exactDeadlineIsExpiredAndElapsedClockResetDoesNotReviveJourney() {
        assertTrue(JourneyPolicy.alive(10_000,1_810_000,1_809_999))
        assertFalse(JourneyPolicy.alive(10_000,1_810_000,1_810_000))
        assertFalse(JourneyPolicy.alive(10_000,1_810_000,9_999))
        assertFalse(JourneyPolicy.alive(-1,100,0))
        assertFalse(JourneyPolicy.alive(100,100,100))
    }
    @Test fun malformedOrOverlongSavedLifetimesCannotBecomeLive() {
        assertFalse(JourneyPolicy.alive(1,1_800_002,2))
        assertFalse(JourneyPolicy.alive(20,10,20))
        assertFalse(JourneyPolicy.alive(0,Long.MAX_VALUE,1))
    }
}
