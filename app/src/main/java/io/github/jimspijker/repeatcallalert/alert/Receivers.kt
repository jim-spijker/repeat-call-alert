package io.github.jimspijker.repeatcallalert.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.stopAsync(context: Context, reason: String) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            AlertService.stop(context, reason)
        } finally {
            pending.finish()
        }
    }
}

/**
 * Receives android.intent.action.PHONE_STATE (exempt from implicit-broadcast limits; needs READ_PHONE_STATE).
 * When a call is answered (OFFHOOK) or ends (IDLE), ringing is over: stop the alert immediately.
 */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> stopAsync(context, "call answered")
            TelephonyManager.EXTRA_STATE_IDLE -> stopAsync(context, "call ended")
        }
    }
}

/** After a reboot: restore an alarm volume that was still raised when the phone went down. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) stopAsync(context, "reboot")
    }
}
