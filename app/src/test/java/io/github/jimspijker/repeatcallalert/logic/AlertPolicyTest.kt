package io.github.jimspijker.repeatcallalert.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    private val silent = 0
    private val vibrate = 1
    private val normal = 2
    private val dndOff = 1
    private val dndPriority = 2
    private val dndUnknown = 0

    @Test fun silentAndVibrateAreSilenced() {
        assertTrue(AlertPolicy.phoneIsSilenced(silent, dndOff))
        assertTrue(AlertPolicy.phoneIsSilenced(vibrate, dndOff))
    }

    @Test fun normalRingerWithoutDndIsNotSilenced() {
        assertFalse(AlertPolicy.phoneIsSilenced(normal, dndOff))
        assertFalse(AlertPolicy.phoneIsSilenced(normal, dndUnknown))
    }

    @Test fun doNotDisturbCountsAsSilenced() {
        assertTrue(AlertPolicy.phoneIsSilenced(normal, dndPriority))
    }

    @Test fun volumeRestoredWhenUntouched() {
        assertEquals(4, AlertPolicy.volumeToRestore(original = 4, applied = 13, current = 13))
    }

    @Test fun volumeNotRestoredWhenUserChangedIt() {
        assertNull(AlertPolicy.volumeToRestore(original = 4, applied = 13, current = 9))
    }

    @Test fun nothingToRestoreWhenNotRaised() {
        assertNull(AlertPolicy.volumeToRestore(original = 12, applied = 12, current = 12))
        assertNull(AlertPolicy.volumeToRestore(original = null, applied = null, current = 3))
    }

    @Test fun targetVolumeRoundsUpAndClamps() {
        assertEquals(13, AlertPolicy.targetVolumeIndex(80, 1, 16)) // 12.8 → 13
        assertEquals(16, AlertPolicy.targetVolumeIndex(100, 1, 16))
        assertEquals(1, AlertPolicy.targetVolumeIndex(1, 0, 16))
        assertEquals(16, AlertPolicy.targetVolumeIndex(500, 0, 16))
        assertEquals(2, AlertPolicy.targetVolumeIndex(1, 2, 16))
    }
}
