package io.github.jimspijker.repeatcallalert.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small on-device activity log (private app file, last [MAX_LINES] lines) so users can see what happened.
 * Never contains full phone numbers. Also written to logcat with tag [TAG] for troubleshooting.
 */
object ActivityLog {
    const val TAG = "RepeatCallAlert"
    private const val MAX_LINES = 100
    private val lock = Any()
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    @Volatile private var loaded = false

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "activity.log")

    fun load(ctx: Context) = synchronized(lock) {
        if (loaded) return
        val f = file(ctx)
        _lines.value = if (f.exists()) f.readLines().takeLast(MAX_LINES) else emptyList()
        loaded = true
    }

    fun log(ctx: Context, message: String) {
        // Logcat only in debuggable builds; release builds keep the log inside the app's private storage.
        if (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) Log.i(TAG, message)
        synchronized(lock) {
            if (!loaded) load(ctx)
            val ts = SimpleDateFormat("MMM d HH:mm:ss", Locale.ENGLISH).format(Date())
            val updated = (_lines.value + "$ts  $message").takeLast(MAX_LINES)
            _lines.value = updated
            runCatching { file(ctx).writeText(updated.joinToString("\n") + "\n") }
        }
    }

    fun clear(ctx: Context) = synchronized(lock) {
        _lines.value = emptyList()
        file(ctx).delete()
    }
}
