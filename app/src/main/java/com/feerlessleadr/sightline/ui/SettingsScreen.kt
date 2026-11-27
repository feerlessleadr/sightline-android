package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import com.feerlessleadr.sightline.data.LockAfter
import com.feerlessleadr.sightline.data.ProbeVariant
import com.feerlessleadr.sightline.data.VideoQuality
import com.feerlessleadr.sightline.video.MotionSensitivity
import com.feerlessleadr.sightline.video.StreamRequest
import com.feerlessleadr.sightline.video.playerStreamRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings, grouped like the web app's Settings sheet (Live view, Video, Security, Account). The web app's
 * other entries arrive with their screens (see docs/feature-parity.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    session: SessionState,
    onBack: () -> Unit,
    onLiveVideo: (Boolean) -> Unit,
    onHighlight: (Boolean) -> Unit,
    onAiLabels: (Boolean) -> Unit,
    onMotionBoxes: (Boolean) -> Unit,
    onMotionSensitivity: (Int) -> Unit,
    onPopup: (com.feerlessleadr.sightline.data.PopupConfig) -> Unit,
    filterSettings: com.feerlessleadr.sightline.data.AlertFilterSettings,
    onQuality: (VideoQuality) -> Unit,
    onResetLayout: () -> Unit,
    onDownloadFolder: (android.net.Uri?) -> Unit,
    onOpeningPicker: () -> Unit,
    onLockAfter: (LockAfter) -> Unit,
    onForgetSaved: () -> Unit,
    onProbe: suspend (camera: String, size: StreamRequest, variant: ProbeVariant) -> String,
    onSignOut: () -> Unit,
    onServerStatus: () -> Unit,
    onSystemLog: () -> Unit,
    onActivity: () -> Unit,
    onAbout: () -> Unit,
    onLegend: () -> Unit,
    onOpenWebPage: () -> Unit,
    onWebPage: (String) -> Boolean,
) {
    var dialog by remember { mutableStateOf<String?>(null) }
    val probeLines = remember { mutableStateListOf<String>() }
    var probing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val metrics = LocalResources.current.displayMetrics
    val context = androidx.compose.ui.platform.LocalContext.current
    val folderPicker =androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) onDownloadFolder(uri) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Live view")
            SwitchRow(
                "Live video in camera tiles",
                "Off shows still pictures every 1.5 seconds instead, which uses less data",
                session.liveVideo,
                onLiveVideo,
            )
            SwitchRow("Highlight active cameras", "A border on tiles with motion (white) or a trigger (red, or the colour of what the AI saw)", session.highlight, onHighlight)
            SwitchRow("AI labels on cameras", "Shows what the AI saw, like \"Person 91%\", while it is still happening", session.aiLabels, onAiLabels)
            SwitchRow("Motion boxes", "Draws a box around movement it finds by comparing pictures (uses a little more data: stills refresh faster)", session.motionBoxes, onMotionBoxes)
            ChoiceRow("Motion box sensitivity", "${session.motionSensitivity} of 10 · ${MotionSensitivity.describe(session.motionSensitivity)}") { dialog = "sensitivity" }
            ChoiceRow("Pop up on detection", session.popup.summary()) { dialog = "popup" }
            ActionRow("Reset layout for this view", "Back to Blue Iris's order, normal tiles and fit-to-screen size for the group shown", onResetLayout)
            ActionRow("Colour legend", "What the colours on tiles, alerts and the timeline mean", onLegend)
            Section("Video")
            ChoiceRow("Video quality", "${session.quality.label} · ${session.quality.note}") { dialog = "quality" }
            Section("Downloads")
            ChoiceRow("Save downloads to", session.downloadFolderName ?: "The Downloads folder") {
                onOpeningPicker()
                try {
                    folderPicker.launch(null)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Couldn't open the folder picker: ${e.javaClass.simpleName}: ${e.message} at ${e.stackTrace.take(3).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName }}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            Text(
                "Pick any folder, on the phone or an SD card. Android does not let apps use the top of the storage or the Downloads folder itself, so make a folder inside it (the picker has a New folder button).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (session.downloadFolder != null) ActionRow("Use the Downloads folder again", "Stops using the folder above", { onDownloadFolder(null) })
            Section("Security")
            if (session.savedSignIn) {
                ChoiceRow("Lock the app", session.lockAfter.label) { dialog = "lock" }
                if (session.lockAfter == LockAfter.Never) {
                    Text(
                        "Anyone who can open your unlocked phone can open your cameras. Your password is stored encrypted on this phone, but no fingerprint is needed to use it.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                ActionRow("Forget saved sign-in", "Deletes the saved password from this phone", onForgetSaved)
            } else {
                Text(
                    "Tick \"Remember me\" when you sign in to unlock with your fingerprint or screen lock instead of typing your password.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Section("Server")
            ActionRow("Server status", "CPU, memory, recording space, frame rates and disks of your Blue Iris server", onServerStatus)
            ActionRow("System log", "Blue Iris's own log: warnings, errors and what it has been doing (needs an admin user)", onSystemLog)
            ActionRow("Activity", "When and where alerts happen: per day and by hour of the day", onActivity)
            Section("Account")
            ActionRow("About Sightline for Android", "Versions, links, copy diagnostics and the licence", onAbout)
            ActionRow("Open Sightline on the web", "Opens ${session.webPage} from your Blue Iris server in the browser (the Sightline page, not UI3)", onOpenWebPage)
            ChoiceRow("Web page file name", session.webPage) { dialog = "webpage" }
            ActionRow("Sign out", null, onSignOut)
            Section("Developer")
            ActionRow(
                "Test video start",
                if (probing) "Running… (about a minute)" else "Opens one camera several ways and times each, to find what slows video down",
            ) {
                if (!probing) {
                    val cam = session.cameras?.cameras?.firstOrNull { it.online }
                    if (cam != null) {
                        probing = true
                        probeLines.clear()
                        scope.launch {
                            val size = playerStreamRequest(
                                metrics.widthPixels / metrics.density, metrics.heightPixels / metrics.density,
                                metrics.density, cam.width ?: 1920, cam.height ?: 1080,
                            )
                            probeLines += "Camera: ${cam.displayName}, asked for ${size.width}x${size.height} ${size.kbps} kbps"
                            val order = ProbeVariant.entries + ProbeVariant.Cookie
                            for ((i, variant) in order.withIndex()) {
                                probeLines += "Running: ${variant.label}…"
                                val result = onProbe(cam.optionValue, size, variant)
                                probeLines[probeLines.lastIndex] = (if (i == order.lastIndex) "(again) " else "") + result
                                delay(2000)
                            }
                            probing = false
                        }
                    }
                }
            }
            if (probeLines.isNotEmpty()) {
                SelectionContainer {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        probeLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (dialog == "popup") PopupSheet(session.popup, filterSettings, onPopup) { dialog = null }
    if (dialog == "webpage") WebPageDialog(session.webPage, onWebPage) { dialog = null }
    when (dialog) {
        "quality" -> ChoiceDialog(
            title = "Video quality",
            options = VideoQuality.entries,
            selected = session.quality,
            label = { "${it.label} · ${it.note}" },
            onPick = { onQuality(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "sensitivity" -> ChoiceDialog(
            title = "Motion box sensitivity",
            options = (1..10).toList(),
            selected = session.motionSensitivity,
            label = { if (it == 5) "5 (balanced)" else it.toString() },
            onPick = { onMotionSensitivity(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "lock" -> ChoiceDialog(
            title = "Lock the app",
            options = LockAfter.entries,
            selected = session.lockAfter,
            label = { it.label },
            onPick = { onLockAfter(it); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HorizontalDivider()
}

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun ActionRow(title: String, note: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

/** A list of choices in a bottom sheet, like the web app's popups. Back or a tap outside closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sl.Surface) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            options.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(option) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = { onPick(option) })
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
            footer?.invoke()
        }
    }
}

/** Asks for the file name of the web version of Sightline on the Blue Iris server (shown as a link on the server's address). */
@Composable
private fun WebPageDialog(current: String, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Web page file name") },
        text = {
            Column {
                Text("The file name of the Sightline web page on your Blue Iris server, for example ${com.feerlessleadr.sightline.data.WebLink.DEFAULT_PAGE}. The link is your sign-in address plus this name.", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text("Use just a file name, like sightline.htm (letters, numbers, dots and dashes).") },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (onSave(text)) onDismiss() else error = true }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
