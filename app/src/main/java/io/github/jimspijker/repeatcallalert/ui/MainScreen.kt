package io.github.jimspijker.repeatcallalert.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jimspijker.repeatcallalert.data.ActivityLog
import io.github.jimspijker.repeatcallalert.data.AppSettings
import io.github.jimspijker.repeatcallalert.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

private val Good = Color(0xFF2E7D32)

@Composable
fun MainScreen(settings: SettingsRepository, setup: SetupStatus, actions: SetupActions) {
    val scope = rememberCoroutineScope()
    val s by settings.settings.collectAsState(initial = AppSettings())

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text("Repeat Call Alert", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Your phone stays silent — unless the same number calls again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatusCard(enabled = s.enabled, ready = setup.ready) { v -> scope.launch { settings.setEnabled(v) } }

            if (!setup.ready || !setup.contacts || !setup.notifications) {
                SetupCard(setup, actions)
            }

            SettingsCard(s, settings)
            TestCard(actions)
            HowItWorksCard()
            ActivityCard()
            FeedbackCard(actions)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusCard(enabled: Boolean, ready: Boolean, onToggle: (Boolean) -> Unit) {
    val (container, onContainer, title, body) = when {
        !enabled -> Quad(
            MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
            "Paused", "Repeat calls will not ring through.",
        )
        !ready -> Quad(
            MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
            "Setup needed", "Finish the steps below to start protecting important calls.",
        )
        else -> Quad(
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
            "Active", "Silent mode stays on. A second call from the same number will ring.",
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

private data class Quad(val a: Color, val b: Color, val c: String, val d: String)

@Composable
private fun SetupCard(setup: SetupStatus, actions: SetupActions) {
    SectionCard("Setup") {
        SetupStep(
            1, "Allow call screening",
            "Lets the app see who is calling before the phone rings. It never blocks calls.",
            setup.screeningRole, "Allow", actions.requestRole,
        )
        if (!setup.screeningRoleAvailable) {
            Text("This phone doesn't offer the call screening role.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        SetupStep(
            2, "Detect when a call ends",
            "Stops the alert as soon as you answer or the call ends.",
            setup.phoneState, "Allow", actions.requestPhoneState,
        )
        SetupStep(
            3, "Run in the background",
            "Turn off battery optimization so Android lets the alert start while the screen is off.",
            setup.battery, "Allow", actions.requestBattery,
        )
        SetupStep(
            4, "Recognise saved contacts (recommended)",
            "Without this, Android only reports calls from numbers that are not in your contacts.",
            setup.contacts, "Allow", actions.requestContacts,
        )
        SetupStep(
            5, "Show a stop button (optional)",
            "A notification lets you silence the alert without answering.",
            setup.notifications, "Allow", actions.requestNotifications,
        )
        TextButton(onClick = actions.openAppInfo) { Text("Open app info") }
    }
}

@Composable
private fun SetupStep(number: Int, title: String, body: String, done: Boolean, button: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).background(if (done) Good else MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else number.toString(),
                color = if (done) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun SettingsCard(s: AppSettings, settings: SettingsRepository) {
    val scope = rememberCoroutineScope()
    SectionCard("Settings") {
        var window by remember(s.windowMinutes) { mutableFloatStateOf(s.windowMinutes.toFloat()) }
        LabelValue("Time window", "${window.roundToInt()} min")
        Slider(
            value = window, onValueChange = { window = it }, valueRange = 1f..30f, steps = 28,
            onValueChangeFinished = { scope.launch { settings.setWindowMinutes(window.roundToInt()) } },
        )
        Text(
            "Calls from the same number within this window count as repeated.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        LabelValue("Ring on call number", "${s.minCalls}")
        val options = listOf(2, 3, 4, 5)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { i, n ->
                SegmentedButton(
                    selected = s.minCalls == n,
                    onClick = { scope.launch { settings.setMinCalls(n) } },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(if (n == 2) "2nd" else if (n == 3) "3rd" else "${n}th") }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        var volume by remember(s.alarmVolumePercent) { mutableFloatStateOf(s.alarmVolumePercent.toFloat()) }
        LabelValue("Minimum alert volume", "${volume.roundToInt()}%")
        Slider(
            value = volume, onValueChange = { volume = it }, valueRange = 10f..100f,
            onValueChangeFinished = { scope.launch { settings.setAlarmVolumePercent(volume.roundToInt()) } },
        )
        Text(
            "If your alarm volume is lower, it is raised for the call and restored afterwards.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TestCard(actions: SetupActions) {
    SectionCard("Test") {
        Text(
            "Put your phone on silent and play a 5-second test. You should hear your ringtone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.testAlert) { Text("Play test alert") }
            OutlinedButton(onClick = actions.stopAlert) { Text("Stop") }
        }
    }
}

private const val REPO_URL = "https://github.com/jim-spijker/repeat-call-alert"

@Composable
private fun FeedbackCard(actions: SetupActions) {
    SectionCard("Ideas welcome") {
        Text(
            "Missing something? Feel free to ask for a feature, like always letting certain contacts ring, quiet hours or support for your phone. No coding needed. Just describe what you'd like.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { actions.openUrl("$REPO_URL/issues/new?template=feature_request.md") }) { Text("Suggest a feature") }
            OutlinedButton(onClick = { actions.openUrl("$REPO_URL/issues/new?template=bug_report.md") }) { Text("Report a problem") }
        }
        TextButton(onClick = { actions.openUrl(REPO_URL) }) { Text("View on GitHub") }
    }
}

@Composable
private fun HowItWorksCard() {
    var open by remember { mutableStateOf(false) }
    SectionCard("How it works", trailing = { ExpandToggle(open) { open = !open } }, onHeaderClick = { open = !open }) {
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bullet("Android lets one app screen incoming calls. This app only looks at the number and always lets the call through.")
                Bullet("When the same number calls again within your time window while your phone is on silent, vibrate or Do Not Disturb, the app plays your ringtone on the alarm channel.")
                Bullet("Why the alarm channel? Some phones, such as OnePlus with the Alert Slider, don't let apps switch the ringer back on. Silent mode doesn't mute alarms, so the alert is still heard.")
                Bullet("The sound stops when you answer, decline or the caller hangs up, or after 60 seconds.")
                Bullet("Withheld or unknown numbers can't be matched and are ignored.")
                Bullet("Privacy: no internet access, no analytics. Numbers are stored only as hashes on this phone, for up to 35 minutes.")
                Bullet("Limitations: it doesn't work before the first unlock after a reboot. With Bluetooth audio connected, the alert may play on that device.")
            }
        }
    }
}

@Composable
private fun ActivityCard() {
    val ctx = LocalContext.current
    val log by ActivityLog.lines.collectAsState()
    var open by remember { mutableStateOf(false) }
    SectionCard("Activity", trailing = { ExpandToggle(open) { open = !open } }, onHeaderClick = { open = !open }) {
        AnimatedVisibility(open) {
            Column {
                if (log.isEmpty()) {
                    Text("Nothing yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    log.takeLast(30).reversed().forEach {
                        Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    TextButton(onClick = { ActivityLog.clear(ctx) }) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth().let { if (onHeaderClick != null) it.clickable(onClick = onHeaderClick) else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ExpandToggle(open: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(if (open) "Hide" else "Show") }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
