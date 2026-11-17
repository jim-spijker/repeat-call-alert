package io.github.jimspijker.repeatcallalert.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatCallDetectorTest {
    private val window = 300_000L // 5 min
    private val t0 = 1_700_000_000_000L

    @Test fun firstCallIsNotRepeat() {
        val d = RepeatCallDetector.register(emptyList(), t0, window, 2)
        assertFalse(d.isRepeat)
        assertEquals(1, d.callsInWindow)
    }

    @Test fun secondCallWithinWindowIsRepeat() {
        val d = RepeatCallDetector.register(listOf(t0), t0 + 120_000, window, 2)
        assertTrue(d.isRepeat)
        assertEquals(2, d.callsInWindow)
    }

    @Test fun secondCallExactlyAtWindowEdgeIsRepeat() {
        assertTrue(RepeatCallDetector.register(listOf(t0), t0 + window, window, 2).isRepeat)
    }

    @Test fun secondCallAfterWindowIsNotRepeat() {
        val d = RepeatCallDetector.register(listOf(t0), t0 + window + 1, window, 2)
        assertFalse(d.isRepeat)
        assertEquals(listOf(t0 + window + 1), d.updatedHistory)
    }

    @Test fun threeCallsRequiredWhenConfigured() {
        val d2 = RepeatCallDetector.register(listOf(t0), t0 + 60_000, window, 3)
        assertFalse(d2.isRepeat)
        assertTrue(RepeatCallDetector.register(d2.updatedHistory, t0 + 120_000, window, 3).isRepeat)
    }

    @Test fun minCallsBelowTwoIsTreatedAsTwo() {
        assertFalse(RepeatCallDetector.register(emptyList(), t0, window, 1).isRepeat)
    }

    @Test fun duplicateCallbackForSameCallIsNotCountedTwice() {
        val d = RepeatCallDetector.register(listOf(t0), t0 + 1_000, window, 2)
        assertTrue(d.isDuplicate)
        assertFalse(d.isRepeat)
        assertEquals(1, d.callsInWindow)
    }

    @Test fun futureTimestampsAfterClockChangeAreIgnored() {
        val d = RepeatCallDetector.register(listOf(t0 + 3_600_000), t0, window, 2)
        assertFalse(d.isRepeat)
        assertEquals(listOf(t0), d.updatedHistory)
    }

    @Test fun historyIsCapped() {
        var h = emptyList<Long>()
        repeat(20) { i -> h = RepeatCallDetector.register(h, t0 + i * 10_000L, window, 2).updatedHistory }
        assertEquals(10, h.size)
    }

    @Test fun codecRoundTripAndPrune() {
        val map = mapOf("abc" to listOf(t0, t0 + 1), "def" to listOf(t0 - 10_000_000))
        val decoded = CallHistoryCodec.decode(CallHistoryCodec.encode(map))
        assertEquals(map, decoded)
        assertEquals(setOf("abc"), CallHistoryCodec.prune(decoded, t0 + 10, 60_000).keys)
    }

    @Test fun codecIgnoresGarbage() {
        assertEquals(emptyMap<String, List<Long>>(), CallHistoryCodec.decode(";;=x;abc=;=1"))
    }
}
