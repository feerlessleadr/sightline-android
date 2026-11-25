package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.data.ExportOptions
import java.text.SimpleDateFormat
import java.util.Date

/**
 * "Download this alert (MP4)": how Blue Iris should make the file (re-encode to H.264, or the original recording), the
 * encoder profile, the overlay and the audio, then the button. The choices are remembered on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    title: String,
    cameraName: String,
    startMs: Long,
    endMs: Long,
    options: ExportOptions,
    onOptions: (ExportOptions) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    sizeText: String = "",
    onChooseRange: (() -> Unit)? = null,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val clock = SimpleDateFormat("h:mm:ss a", locale)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sl.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                cameraName + " · " + SimpleDateFormat("EEE MMM d", locale).format(Date(startMs)),
                style = MaterialTheme.typography.bodySmall,
                color = Sl.Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "${clock.format(Date(startMs))} to ${clock.format(Date(endMs))}, about ${duration(endMs - startMs)}" + (if (sizeText.isNotEmpty()) " · $sizeText" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = Sl.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            SectionLabel("Video")
            ModeRow(
                "Re-encode to H.264",
                "Blue Iris converts it to standard H.264, which plays smoothly anywhere. Takes longer. Quality comes from the encoder profile below.",
                options.reencode,
            ) { onOptions(options.copy(reencode = true)) }
            ModeRow(
                "Original recording",
                "Some players may show as choppy. Exactly as the camera recorded it, nothing lost and fastest option.",
                !options.reencode,
            ) { onOptions(options.copy(reencode = false)) }

            if (options.reencode) {
                Text("Blue Iris encoder profile", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 0..3) Pill("export $n", selected = options.profile == n) { onOptions(options.copy(profile = n)) }
                }
                SwitchRow(
                    "Add the camera's text and graphic overlay",
                    "Timestamp and other overlays the camera currently shows in Blue Iris",
                    options.overlay,
                ) { onOptions(options.copy(overlay = it)) }
            }
            SwitchRow("Include audio", "The camera's sound, if it records any", options.audio) { onOptions(options.copy(audio = it)) }

            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Download MP4") }
            if (onChooseRange != null) {
                OutlinedButton(onClick = onChooseRange, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Choose start and end…") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Sl.Accent, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun ModeRow(title: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = Sl.Muted)
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = Sl.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun duration(ms: Long): String {
    val seconds = (ms / 1000.0).toLong().coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
