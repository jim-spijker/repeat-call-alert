package io.github.jimspijker.repeatcallalert.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jimspijker.repeatcallalert.logic.CallHistoryCodec
import io.github.jimspijker.repeatcallalert.logic.RepeatCallDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.stateStore: DataStore<Preferences> by preferencesDataStore(name = "state")

data class AppSettings(
    val enabled: Boolean = true,
    val windowMinutes: Int = 5,
    val minCalls: Int = 2,
    val alarmVolumePercent: Int = 80,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val enabled = booleanPreferencesKey("enabled")
        val window = intPreferencesKey("window_minutes")
        val minCalls = intPreferencesKey("min_calls")
        val volume = intPreferencesKey("alarm_volume_percent")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            enabled = p[K.enabled] ?: true,
            windowMinutes = (p[K.window] ?: 5).coerceIn(1, 30),
            minCalls = (p[K.minCalls] ?: 2).coerceIn(2, 5),
            alarmVolumePercent = (p[K.volume] ?: 80).coerceIn(10, 100),
        )
    }

    suspend fun current(): AppSettings = settings.first()
    suspend fun setEnabled(v: Boolean) = context.settingsStore.edit { it[K.enabled] = v }
    suspend fun setWindowMinutes(v: Int) = context.settingsStore.edit { it[K.window] = v.coerceIn(1, 30) }
    suspend fun setMinCalls(v: Int) = context.settingsStore.edit { it[K.minCalls] = v.coerceIn(2, 5) }
    suspend fun setAlarmVolumePercent(v: Int) = context.settingsStore.edit { it[K.volume] = v.coerceIn(10, 100) }
}

/**
 * Call history: only a salted SHA-256 hash per number plus timestamps — never the number itself.
 * The salt is random per installation, so hashes can't be matched against a precomputed table of numbers.
 */
class CallHistoryStore(private val context: Context) {
    private val key = stringPreferencesKey("call_history")
    private val saltKey = stringPreferencesKey("hash_salt")

    suspend fun registerCall(numberKey: String, now: Long, windowMs: Long, minCalls: Int): RepeatCallDetector.Decision {
        var decision: RepeatCallDetector.Decision? = null
        context.stateStore.edit { p ->
            val salt = p[saltKey] ?: newSalt().also { p[saltKey] = it }
            val all = CallHistoryCodec.prune(CallHistoryCodec.decode(p[key]), now, MAX_AGE_MS).toMutableMap()
            val hashed = hash(salt, numberKey)
            val d = RepeatCallDetector.register(all[hashed].orEmpty(), now, windowMs, minCalls)
            all[hashed] = d.updatedHistory
            p[key] = CallHistoryCodec.encode(all)
            decision = d
        }
        return decision!!
    }

    companion object {
        /** Longest configurable window (30 min) plus margin; anything older is deleted. */
        const val MAX_AGE_MS = 35 * 60_000L

        fun hash(salt: String, s: String): String {
            val md = MessageDigest.getInstance("SHA-256").digest("$salt:$s".toByteArray())
            return md.joinToString("") { "%02x".format(it) }
        }

        private fun newSalt(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Remembers a temporarily raised alarm volume so it can be restored, even after the process died. */
class AlarmVolumeStore(private val context: Context) {
    private object K {
        val original = intPreferencesKey("alarm_original_volume")
        val applied = intPreferencesKey("alarm_applied_volume")
    }

    data class State(val originalVolume: Int, val appliedVolume: Int)

    suspend fun get(): State? {
        val p = context.stateStore.data.first()
        return State(p[K.original] ?: return null, p[K.applied] ?: return null)
    }

    suspend fun save(s: State) = context.stateStore.edit { it[K.original] = s.originalVolume; it[K.applied] = s.appliedVolume }
    suspend fun clear() = context.stateStore.edit { it.remove(K.original); it.remove(K.applied) }
}
