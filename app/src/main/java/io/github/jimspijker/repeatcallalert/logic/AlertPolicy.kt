package io.github.jimspijker.repeatcallalert.logic

/** Pure decisions about when to sound the alert and how to restore the alarm volume afterwards. */
object AlertPolicy {

    // Same values as android.media.AudioManager.RINGER_MODE_NORMAL and NotificationManager.INTERRUPTION_FILTER_ALL
    const val RINGER_MODE_NORMAL = 2
    const val INTERRUPTION_FILTER_ALL = 1

    /**
     * Only sound the alert when the phone would otherwise stay quiet (silent/vibrate, or Do Not Disturb).
     * When the phone already rings normally, the app stays out of the way.
     */
    fun phoneIsSilenced(ringerMode: Int, interruptionFilter: Int): Boolean =
        ringerMode != RINGER_MODE_NORMAL || (interruptionFilter != 0 && interruptionFilter != INTERRUPTION_FILTER_ALL)

    /** Target volume index for a percentage (1..100), rounded up and clamped to the stream's range. */
    fun targetVolumeIndex(percent: Int, minIndex: Int, maxIndex: Int): Int {
        val p = percent.coerceIn(1, 100)
        val idx = (maxIndex * p + 99) / 100
        return idx.coerceIn(maxOf(1, minIndex), maxIndex)
    }

    /**
     * Volume to restore after a temporary raise, or `null` when there is nothing to restore or the user
     * changed the volume in the meantime (their choice always wins).
     */
    fun volumeToRestore(original: Int?, applied: Int?, current: Int): Int? {
        if (original == null || applied == null) return null
        if (original == applied) return null
        if (current != applied) return null
        return original
    }
}
