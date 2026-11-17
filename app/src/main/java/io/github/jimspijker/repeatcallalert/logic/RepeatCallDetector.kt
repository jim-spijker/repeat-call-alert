package io.github.jimspijker.repeatcallalert.logic

/**
 * Pure logic that decides whether an incoming call is a repeat call from the same number.
 * Times are wall-clock milliseconds (System.currentTimeMillis()).
 */
object RepeatCallDetector {

    /** Two screenings closer together than this are treated as the same call (duplicate callback). */
    const val DUPLICATE_MARGIN_MS = 3_000L
    private const val MAX_ENTRIES_PER_NUMBER = 10

    data class Decision(
        /** Updated, pruned history for this number (including this call). */
        val updatedHistory: List<Long>,
        /** Number of calls inside the window, including this one. */
        val callsInWindow: Int,
        val isRepeat: Boolean,
        val isDuplicate: Boolean,
    )

    fun register(history: List<Long>, now: Long, windowMs: Long, minCalls: Int): Decision {
        require(windowMs > 0) { "windowMs must be positive" }
        val required = minCalls.coerceAtLeast(2)
        // Ignore timestamps in the future (clock change) and those outside the window
        val inWindow = history.filter { it <= now && now - it <= windowMs }.sorted()
        val last = inWindow.lastOrNull()
        val duplicate = last != null && now - last < DUPLICATE_MARGIN_MS
        val updated = if (duplicate) inWindow else (inWindow + now)
        val trimmed = updated.takeLast(MAX_ENTRIES_PER_NUMBER)
        return Decision(
            updatedHistory = trimmed,
            callsInWindow = trimmed.size,
            isRepeat = trimmed.size >= required,
            isDuplicate = duplicate,
        )
    }
}

/**
 * Compact local storage format for call times per (hashed) number key:
 * "key=t1,t2;key2=t3". Keys are hex hashes and never contain '=' or ';'.
 */
object CallHistoryCodec {

    fun decode(encoded: String?): Map<String, List<Long>> {
        if (encoded.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, List<Long>>()
        for (entry in encoded.split(';')) {
            val idx = entry.indexOf('=')
            if (idx <= 0) continue
            val key = entry.substring(0, idx)
            val times = entry.substring(idx + 1).split(',').mapNotNull { it.toLongOrNull() }
            if (times.isNotEmpty()) result[key] = times
        }
        return result
    }

    fun encode(map: Map<String, List<Long>>): String =
        map.filterValues { it.isNotEmpty() }
            .entries.joinToString(";") { (k, v) -> k + "=" + v.joinToString(",") }

    /** Drops everything older than [maxAgeMs] to keep storage small and private. */
    fun prune(map: Map<String, List<Long>>, now: Long, maxAgeMs: Long): Map<String, List<Long>> =
        map.mapValues { (_, v) -> v.filter { it <= now && now - it <= maxAgeMs } }
            .filterValues { it.isNotEmpty() }
}
