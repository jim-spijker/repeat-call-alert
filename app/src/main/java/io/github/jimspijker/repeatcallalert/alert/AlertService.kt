package io.github.jimspijker.repeatcallalert.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import io.github.jimspijker.repeatcallalert.R
import io.github.jimspijker.repeatcallalert.data.ActivityLog
import io.github.jimspijker.repeatcallalert.data.AlarmVolumeStore
import io.github.jimspijker.repeatcallalert.logic.AlertPolicy
import io.github.jimspijker.repeatcallalert.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays the user's ringtone on the ALARM stream. Silent mode (including the OnePlus Alert Slider)
 * does not mute that stream, while apps are not allowed to switch the ringer back on.
 *
 * Runs as a short-lived foreground service (type mediaPlayback): Android freezes background apps and,
 * since Android 15, only grants audio focus to apps that are visible or run a foreground service.
 *
 * Stops when the call is answered or ends ([CallStateReceiver]), via the notification, or after [MAX_DURATION_MS].
 */
class AlertService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var timeoutJob: Job? = null
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundNow(intent.getBooleanExtra(EXTRA_IS_TEST, false))
                running = true
                if (player == null) {
                    val pct = intent.getIntExtra(EXTRA_VOLUME_PCT, 80)
                    val maxMs = intent.getLongExtra(EXTRA_MAX_MS, MAX_DURATION_MS)
                    scope.launch { startPlayback(pct, maxMs) }
                }
            }
            ACTION_STOP -> scope.launch { stopAlert(intent.getStringExtra(EXTRA_REASON) ?: "stopped") }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNow(isTest: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_description)
                setSound(null, null)
            }
        )
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, AlertService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_REASON, "notification button"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openPi = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (isTest) R.string.notification_title_test else R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openPi)
            .addAction(Notification.Action.Builder(null, getString(R.string.notification_stop), stopPi).build())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private suspend fun startPlayback(volumePercent: Int, maxMs: Long) {
        val am = getSystemService(AudioManager::class.java)
        val stream = AudioManager.STREAM_ALARM
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Raise the alarm volume to the configured minimum and remember the original value
        val original = am.getStreamVolume(stream)
        val target = AlertPolicy.targetVolumeIndex(volumePercent, am.getStreamMinVolume(stream), am.getStreamMaxVolume(stream))
        if (original < target) runCatching { am.setStreamVolume(stream, target, 0) }
        val applied = am.getStreamVolume(stream)
        if (applied != original) AlarmVolumeStore(this).save(AlarmVolumeStore.State(original, applied))

        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
        focusRequest = focus
        val focusGranted = am.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        val uri: Uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_RINGTONE_URI
        val error = runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlertService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.exceptionOrNull()

        if (error != null) {
            ActivityLog.log(this, "Could not play ringtone: ${error.javaClass.simpleName}: ${error.message}")
            stopAlert("playback failed")
            return
        }
        ActivityLog.log(
            this,
            "Ringing on alarm channel (volume $applied/${am.getStreamMaxVolume(stream)}" +
                (if (focusGranted) "" else ", audio focus denied") + ")."
        )
        timeoutJob = scope.launch {
            delay(maxMs)
            stopAlert("time limit reached")
        }
    }

    private suspend fun stopAlert(reason: String) {
        if (stopping) return
        stopping = true
        timeoutJob?.cancel()
        val wasPlaying = player != null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        restoreVolume(this)
        if (wasPlaying) ActivityLog.log(this, "Stopped ringing ($reason).")
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        runCatching { player?.release() }
        player = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "repeat_call_alert"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "io.github.jimspijker.repeatcallalert.START"
        private const val ACTION_STOP = "io.github.jimspijker.repeatcallalert.STOP"
        private const val EXTRA_VOLUME_PCT = "volume_pct"
        private const val EXTRA_MAX_MS = "max_ms"
        private const val EXTRA_IS_TEST = "is_test"
        private const val EXTRA_REASON = "reason"
        const val MAX_DURATION_MS = 60_000L
        const val TEST_DURATION_MS = 5_000L

        @Volatile var running = false
            private set

        /** Starts the alert. Returns false when Android refuses to start the service. */
        fun start(context: Context, volumePercent: Int, isTest: Boolean = false): Boolean {
            val i = Intent(context, AlertService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_VOLUME_PCT, volumePercent)
                .putExtra(EXTRA_IS_TEST, isTest)
                .putExtra(EXTRA_MAX_MS, if (isTest) TEST_DURATION_MS else MAX_DURATION_MS)
            return try {
                context.startForegroundService(i)
                true
            } catch (e: Exception) {
                ActivityLog.log(
                    context,
                    "Android blocked the alert (${e.javaClass.simpleName}). Turn off battery optimization for this app.",
                )
                false
            }
        }

        /** Stops the alert. If the service is no longer running, only restores the alarm volume. */
        suspend fun stop(context: Context, reason: String) {
            if (running) {
                runCatching {
                    context.startService(Intent(context, AlertService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_REASON, reason))
                }.onFailure { restoreVolume(context) }
            } else {
                restoreVolume(context)
            }
        }

        suspend fun restoreVolume(context: Context) {
            val store = AlarmVolumeStore(context)
            val s = store.get() ?: return
            val am = context.getSystemService(AudioManager::class.java)
            val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
            store.clear()
            val restoreTo = AlertPolicy.volumeToRestore(s.originalVolume, s.appliedVolume, current) ?: return
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, restoreTo, 0) }
        }
    }
}
