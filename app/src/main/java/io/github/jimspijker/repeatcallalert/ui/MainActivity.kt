package io.github.jimspijker.repeatcallalert.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.jimspijker.repeatcallalert.alert.AlertService
import io.github.jimspijker.repeatcallalert.data.ActivityLog
import io.github.jimspijker.repeatcallalert.data.SettingsRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsRepository
    private val setup = mutableStateOf(SetupStatus())

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = SettingsRepository(this)
        ActivityLog.load(this)
        setContent {
            AppTheme {
                MainScreen(
                    settings = settings,
                    setup = setup.value,
                    actions = SetupActions(
                        requestRole = ::requestScreeningRole,
                        requestPhoneState = { permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                        requestContacts = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        requestBattery = {
                            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null)))
                        },
                        requestNotifications = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        openAppInfo = {
                            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                        },
                        testAlert = {
                            lifecycleScope.launch { AlertService.start(this@MainActivity, settings.current().alarmVolumePercent, isTest = true) }
                        },
                        stopAlert = { lifecycleScope.launch { AlertService.stop(this@MainActivity, "stopped in app") } },
                        openUrl = { url -> open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        openContacts = { open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)) },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val rm = getSystemService(RoleManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        setup.value = SetupStatus(
            screeningRole = rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
            screeningRoleAvailable = rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING),
            phoneState = granted(Manifest.permission.READ_PHONE_STATE),
            contacts = granted(Manifest.permission.READ_CONTACTS),
            battery = pm.isIgnoringBatteryOptimizations(packageName),
            notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun requestScreeningRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
}

data class SetupStatus(
    val screeningRole: Boolean = false,
    val screeningRoleAvailable: Boolean = true,
    val phoneState: Boolean = false,
    val contacts: Boolean = false,
    val battery: Boolean = false,
    val notifications: Boolean = false,
) {
    /** Everything needed for the core feature. Contacts and notifications are optional. */
    val ready: Boolean get() = screeningRole && phoneState && battery
}

class SetupActions(
    val requestRole: () -> Unit,
    val requestPhoneState: () -> Unit,
    val requestContacts: () -> Unit,
    val requestBattery: () -> Unit,
    val requestNotifications: () -> Unit,
    val openAppInfo: () -> Unit,
    val testAlert: () -> Unit,
    val stopAlert: () -> Unit,
    val openUrl: (String) -> Unit,
    val openContacts: () -> Unit,
)
