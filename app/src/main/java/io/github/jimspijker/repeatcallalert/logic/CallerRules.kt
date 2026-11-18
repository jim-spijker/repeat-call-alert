package io.github.jimspijker.repeatcallalert.logic

/**
 * Who is calling, as far as the app can tell. "Starred" contacts are the favourites in the Contacts/Phone app,
 * the same list Android's Do Not Disturb uses for "Starred contacts".
 */
enum class CallerType { UNKNOWN, CONTACT, STARRED }

/** Who rings through on the FIRST call (like DND "Calls from"). */
enum class FirstCallFrom(val id: Int) {
    NOBODY(0), STARRED(1), CONTACTS(2);

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: NOBODY
    }
}

/** Whose REPEATED calls ring through (like DND "Repeat callers", optionally limited to contacts). */
enum class RepeatFrom(val id: Int) {
    ANYONE(0), CONTACTS(1), STARRED(2);

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: ANYONE
    }
}

sealed interface RingDecision {
    data object Stay : RingDecision
    data class Ring(val reason: String) : RingDecision
}

object CallerRules {

    /** True when this caller type satisfies a "contacts" or "starred" requirement. */
    private fun isContact(t: CallerType) = t == CallerType.CONTACT || t == CallerType.STARRED

    fun allowsFirstCall(rule: FirstCallFrom, caller: CallerType): Boolean = when (rule) {
        FirstCallFrom.NOBODY -> false
        FirstCallFrom.STARRED -> caller == CallerType.STARRED
        FirstCallFrom.CONTACTS -> isContact(caller)
    }

    fun allowsRepeat(rule: RepeatFrom, caller: CallerType): Boolean = when (rule) {
        RepeatFrom.ANYONE -> true
        RepeatFrom.CONTACTS -> isContact(caller)
        RepeatFrom.STARRED -> caller == CallerType.STARRED
    }

    /**
     * Decide whether a call should ring through silent mode.
     * The first-call rule wins on any call; otherwise the repeat rule applies once the call count is reached.
     */
    fun decide(caller: CallerType, isRepeat: Boolean, firstCall: FirstCallFrom, repeat: RepeatFrom): RingDecision {
        if (allowsFirstCall(firstCall, caller)) {
            return RingDecision.Ring(if (caller == CallerType.STARRED) "starred contact" else "contact")
        }
        if (isRepeat && allowsRepeat(repeat, caller)) return RingDecision.Ring("repeated call")
        return RingDecision.Stay
    }

    /** Whether looking up the caller in the contacts is needed for these settings. */
    fun needsContactLookup(firstCall: FirstCallFrom, repeat: RepeatFrom): Boolean =
        firstCall != FirstCallFrom.NOBODY || repeat != RepeatFrom.ANYONE
}
