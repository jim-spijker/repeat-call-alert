package io.github.jimspijker.repeatcallalert.screening

import android.app.NotificationManager
import android.media.AudioManager
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import io.github.jimspijker.repeatcallalert.alert.AlertService
import io.github.jimspijker.repeatcallalert.data.ActivityLog
import io.github.jimspijker.repeatcallalert.data.CallHistoryStore
import io.github.jimspijker.repeatcallalert.data.SettingsRepository
import io.github.jimspijker.repeatcallalert.logic.AlertPolicy
import io.github.jimspijker.repeatcallalert.logic.PhoneNumberNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bound by Android's Telecom service before an incoming call starts ringing (requires the user-approved
 * call screening role). The phone does not ring until we respond, so the alert starts before ringing.
 *
 * This service NEVER blocks, rejects or silences a call: the response is always "allow" and is always
 * sent within [RESPONSE_BUDGET_MS] (Android allows 5 seconds).
 */
class RepeatCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onScreenCall(callDetails: Call.Details) {
        val responded = AtomicBoolean(false)
        scope.launch {
            withTimeoutOrNull(RESPONSE_BUDGET_MS) { runCatching { handle(callDetails) } }
            if (responded.compareAndSet(false, true)) {
                runCatching { respondToCall(callDetails, CallResponse.Builder().build()) }
            }
        }
    }

    private suspend fun handle(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val settings = SettingsRepository(this).current()
        if (!settings.enabled) return
        val raw = details.handle?.schemeSpecificPart
        if (details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED) {
            ActivityLog.log(this, "Incoming call from a hidden number — can't be matched.")
            return
        }
        val iso = getSystemService(TelephonyManager::class.java)?.let { tm ->
            tm.networkCountryIso?.takeIf { it.isNotBlank() } ?: tm.simCountryIso
        }
        val key = PhoneNumberNormalizer.normalize(raw, PhoneNumberNormalizer.callingCodeForIso(iso))
        if (key == null) {
            ActivityLog.log(this, "Incoming call from ${PhoneNumberNormalizer.mask(raw)} — number not usable for matching.")
            return
        }
        val d = CallHistoryStore(this).registerCall(key, System.currentTimeMillis(), settings.windowMinutes * 60_000L, settings.minCalls)
        if (d.isDuplicate) return
        val masked = PhoneNumberNormalizer.mask(raw)
        if (!d.isRepeat) {
            ActivityLog.log(this, "Call from $masked (${d.callsInWindow}× in ${settings.windowMinutes} min).")
            return
        }
        val audio = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        if (!AlertPolicy.phoneIsSilenced(audio.ringerMode, nm.currentInterruptionFilter)) {
            ActivityLog.log(this, "Repeat call from $masked — phone already rings normally, nothing to do.")
            return
        }
        ActivityLog.log(this, "Repeat call from $masked (${d.callsInWindow}× in ${settings.windowMinutes} min) — sounding alert.")
        AlertService.start(this, settings.alarmVolumePercent)
    }

    companion object {
        /** Well within the 5 seconds Telecom allows. */
        const val RESPONSE_BUDGET_MS = 3_500L
    }
}
