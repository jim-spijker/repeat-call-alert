package io.github.jimspijker.repeatcallalert.logic

import io.github.jimspijker.repeatcallalert.logic.CallerType.CONTACT
import io.github.jimspijker.repeatcallalert.logic.CallerType.STARRED
import io.github.jimspijker.repeatcallalert.logic.CallerType.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerRulesTest {

    private fun rings(caller: CallerType, repeat: Boolean, first: FirstCallFrom, rep: RepeatFrom) =
        CallerRules.decide(caller, repeat, first, rep) is RingDecision.Ring

    // Default behaviour (1.0.x): anyone's repeated call rings, first calls never do
    @Test fun defaultsRingOnlyRepeatedCallsFromAnyone() {
        for (c in CallerType.entries) {
            assertFalse(rings(c, repeat = false, FirstCallFrom.NOBODY, RepeatFrom.ANYONE))
            assertTrue(rings(c, repeat = true, FirstCallFrom.NOBODY, RepeatFrom.ANYONE))
        }
    }

    @Test fun starredRingOnFirstCall() {
        assertTrue(rings(STARRED, repeat = false, FirstCallFrom.STARRED, RepeatFrom.ANYONE))
        assertFalse(rings(CONTACT, repeat = false, FirstCallFrom.STARRED, RepeatFrom.ANYONE))
        assertFalse(rings(UNKNOWN, repeat = false, FirstCallFrom.STARRED, RepeatFrom.ANYONE))
    }

    @Test fun allContactsRingOnFirstCall() {
        assertTrue(rings(STARRED, repeat = false, FirstCallFrom.CONTACTS, RepeatFrom.ANYONE))
        assertTrue(rings(CONTACT, repeat = false, FirstCallFrom.CONTACTS, RepeatFrom.ANYONE))
        assertFalse(rings(UNKNOWN, repeat = false, FirstCallFrom.CONTACTS, RepeatFrom.ANYONE))
    }

    @Test fun repeatLimitedToContacts() {
        assertTrue(rings(CONTACT, repeat = true, FirstCallFrom.NOBODY, RepeatFrom.CONTACTS))
        assertTrue(rings(STARRED, repeat = true, FirstCallFrom.NOBODY, RepeatFrom.CONTACTS))
        assertFalse(rings(UNKNOWN, repeat = true, FirstCallFrom.NOBODY, RepeatFrom.CONTACTS))
    }

    @Test fun repeatLimitedToStarred() {
        assertTrue(rings(STARRED, repeat = true, FirstCallFrom.NOBODY, RepeatFrom.STARRED))
        assertFalse(rings(CONTACT, repeat = true, FirstCallFrom.NOBODY, RepeatFrom.STARRED))
        assertFalse(rings(UNKNOWN, repeat = true, FirstCallFrom.NOBODY, RepeatFrom.STARRED))
    }

    @Test fun onlyStarredEverRing() {
        // "Only my favourites, right away": first call starred, repeats starred only
        assertTrue(rings(STARRED, repeat = false, FirstCallFrom.STARRED, RepeatFrom.STARRED))
        assertFalse(rings(CONTACT, repeat = true, FirstCallFrom.STARRED, RepeatFrom.STARRED))
        assertFalse(rings(UNKNOWN, repeat = true, FirstCallFrom.STARRED, RepeatFrom.STARRED))
    }

    @Test fun reasonNamesTheRule() {
        assertEquals(RingDecision.Ring("starred contact"), CallerRules.decide(STARRED, false, FirstCallFrom.STARRED, RepeatFrom.ANYONE))
        assertEquals(RingDecision.Ring("repeated call"), CallerRules.decide(UNKNOWN, true, FirstCallFrom.STARRED, RepeatFrom.ANYONE))
    }

    @Test fun lookupOnlyNeededForContactRules() {
        assertFalse(CallerRules.needsContactLookup(FirstCallFrom.NOBODY, RepeatFrom.ANYONE))
        assertTrue(CallerRules.needsContactLookup(FirstCallFrom.STARRED, RepeatFrom.ANYONE))
        assertTrue(CallerRules.needsContactLookup(FirstCallFrom.NOBODY, RepeatFrom.CONTACTS))
    }

    @Test fun unknownIdsFallBackToDefaults() {
        assertEquals(FirstCallFrom.NOBODY, FirstCallFrom.of(99))
        assertEquals(RepeatFrom.ANYONE, RepeatFrom.of(-1))
    }
}
