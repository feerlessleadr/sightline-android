package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.data.AlertFilterSettings
import com.feerlessleadr.sightline.data.AlertFilters
import com.feerlessleadr.sightline.data.Popup
import com.feerlessleadr.sightline.data.PopupConfig

/** "Pop up on detection": which kinds and labels make a camera pop up on the grid. Labels come from the same 3-week scan as Alert filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopupSheet(config: PopupConfig, filterSettings: AlertFilterSettings, onChange: (PopupConfig) -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { filterSettings.scan() }
    val scan by filterSettings.state.collectAsState()
    // Choosing something turns pop-ups on again if they were switched off.
    val choose: (PopupConfig) -> Unit = { onChange(it.copy(enabled = true)) }
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val submit = {
        if (input.isNotBlank()) {
            val k = AlertFilters.normLabel(input.trim().lowercase())
            val (next, text) = Popup.addLabel(config, input, scan.counts?.get(k)?.n, scan.counts != null)
            note = text
            if (next != config) { choose(next); input = "" }
        }
    }
    // Labels turned on, then the ones the cameras reported lately (most common first); People, Vehicles and Animals have their own switches.
    val rows = LinkedHashMap<String, String>()
    fun add(label: String) { val k = AlertFilters.normLabel(label); if (k.isNotEmpty() && k !in rows) rows[k] = label }
    config.labels.forEach(::add)
    scan.counts?.entries?.sortedByDescending { it.value.n }?.forEach { (k, v) -> if (k !in setOf("person", "vehicle", "animal")) add(v.name) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sl.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Pop up on detection", style = MaterialTheme.typography.titleLarge, color = Sl.Text)
            Text(
                if (config.isOn) "A camera pops up when it sees ${config.summary().lowercase()}. Only for cameras in the group on screen, while the camera grid is showing. Plain motion never does."
                else "Pick what makes a camera pop up. Plain motion never does.",
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            ToggleRow(
                "Pop up cameras",
                when {
                    config.isOn -> "On"
                    !config.hasChoices -> "Off: choose something below to turn it on"
                    else -> "Off: your choices below are kept"
                },
                config.enabled && config.hasChoices,
                null,
            ) { onChange(config.copy(enabled = !(config.enabled && config.hasChoices))) }
            Heading("What pops up")
            for ((kind, name) in Popup.KINDS) {
                ToggleRow(name, "", kind in config.kinds, null) { choose(config.withKind(kind, kind !in config.kinds)) }
            }
            Heading("Labels")
            if (rows.isEmpty()) {
                Text(
                    if (scan.scanning) "Looking at the last 3 weeks of alerts…" else "No other AI labels found in the last 3 weeks. Add one below.",
                    color = Sl.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            for ((k, label) in rows) {
                val count = scan.counts?.get(k)
                val sub = when {
                    count != null -> "${count.n} alert${if (count.n == 1) "" else "s"} in the last 3 weeks"
                    scan.scanning -> "Checking…"
                    scan.counts != null -> "Not seen in the last 3 weeks"
                    else -> ""
                }
                val on = config.hasLabel(label)
                ToggleRow(AlertFilters.name(AlertFilters.label(label)), sub, on, null) { choose(config.withLabel(label, !on)) }
            }
            if (scan.scanning && rows.isNotEmpty()) {
                Text("Looking at the last 3 weeks of alerts for more labels…", color = Sl.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(30); note = "" },
                    placeholder = { Text("Add a label, e.g. ups") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { submit() }) { Text("Add") }
            }
            if (note.isNotEmpty()) Text(note, color = Sl.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Done") }
        }
    }
}
