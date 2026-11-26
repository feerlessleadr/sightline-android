package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.feerlessleadr.sightline.data.AlertFilterConfig
import com.feerlessleadr.sightline.data.AlertFilterSettings
import com.feerlessleadr.sightline.data.AlertFilters
import com.feerlessleadr.sightline.data.FilterSettingsUi
import com.feerlessleadr.sightline.data.sameFilter

/** The "Alert filters" sheet: Choose or Auto, the built-in buttons, labels seen on the cameras, and the owner's own labels. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AlertFiltersSheet(ui: FilterSettingsUi, settings: AlertFilterSettings, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { settings.scan() }
    val c = ui.config
    var input by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val submit = {
        if (input.isNotBlank()) {
            note = settings.addLabel(input)
            if (note.startsWith("Added")) input = ""
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sl.Surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Alert filters", style = MaterialTheme.typography.titleLarge, color = Sl.Text)
            Text(
                if (c.auto) {
                    "Auto shows All, the filters you always show (below), then the detections your cameras have seen most in the last 3 weeks. Capitalization and plurals don’t matter: “Dog”, “dog” and “dogs” are the same."
                } else {
                    "Choose the filter buttons on the Alerts tab. Labels match the AI label Blue Iris saved with each alert. Capitalization and plurals don’t matter: “Dog”, “dog” and “dogs” are the same."
                },
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Heading("Buttons on the Alerts tab")
            ModeRow("Auto", "All, anything you always show, then the most common detections of the last 3 weeks", c.auto) { settings.setAuto(true) }
            ModeRow("Choose", "Pick exactly which buttons show", !c.auto) { settings.setAuto(false) }

            if (c.auto) {
                val recent = c.recent.filter { r -> c.pins.none { sameFilter(it, r.id) } }
                Text(
                    if (recent.isNotEmpty()) {
                        "Most common lately: " + recent.take(8).joinToString(" · ") { "${AlertFilters.name(it.id)} ${it.n}" } + "."
                    } else if (ui.scanning) {
                        "Looking at the last 3 weeks of alerts…"
                    } else {
                        "Nothing detected in the last 3 weeks yet."
                    },
                    color = Sl.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text("How many of these to show", color = Sl.Text, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in listOf(0, 1, 2, 3, 4, 5, 6, 8)) {
                        FilterChip(selected = c.count == v, onClick = { settings.setCount(v) }, label = { Text(if (v == 0) "Fit screen" else v.toString()) })
                    }
                }
                Text(
                    if (c.count == 0) "Up to ${AlertFilterConfig.AUTO_MAX}, after the ones you always show." else "Always ${c.count}, after the ones you always show.",
                    color = Sl.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Heading("Always show")
            } else {
                Heading("Built in")
            }
            val active = c.active()
            for (id in AlertFilters.DEFAULT) {
                if (c.auto && id == AlertFilters.ALL) continue
                ToggleRow(AlertFilters.name(id), builtInNote(id), active.any { sameFilter(it, id) }, null) { settings.toggle(id) }
            }

            Heading("Labels")
            val rows = labelRows(c, ui)
            if (rows.isEmpty()) {
                Text(
                    if (ui.scanning) "Looking at the last 3 weeks of alerts…" else "No AI labels found in the last 3 weeks. Add one below.",
                    color = Sl.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            for (r in rows) {
                val id = AlertFilters.label(r.label)
                val count = ui.counts?.get(r.key)
                val sub = when {
                    count != null -> "${count.n} alert${if (count.n == 1) "" else "s"} in the last 3 weeks"
                    ui.scanning -> "Checking…"
                    ui.counts != null -> "Not seen in the last 3 weeks"
                    else -> ""
                }
                val on = active.any { sameFilter(it, id) }
                ToggleRow(AlertFilters.name(id), sub, on, if (r.custom && count == null && !on) ({ settings.removeLabel(r.label) }) else null) { settings.toggle(id) }
            }
            if (ui.scanning && rows.isNotEmpty()) {
                Text("Looking at the last 3 weeks of alerts for more labels…", color = Sl.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }

            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(30); note = "" },
                    placeholder = { Text("Add a label, e.g. cat") },
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

private class LabelRow(val key: String, val label: String, val custom: Boolean)

/** Labels that are on, the owner's own, then everything the cameras have reported lately (most common first), one row per word. */
private fun labelRows(c: AlertFilterConfig, ui: FilterSettingsUi): List<LabelRow> {
    val rows = LinkedHashMap<String, LabelRow>()
    fun add(label: String, custom: Boolean = false) {
        val k = AlertFilters.normLabel(label)
        if (k.isEmpty()) return
        val old = rows[k]
        rows[k] = LabelRow(k, old?.label ?: label, custom || old?.custom == true)
    }
    c.active().filter { it.startsWith("label:") }.forEach { add(it.removePrefix("label:")) }
    c.labels.forEach { add(it, custom = true) }
    ui.counts?.entries?.sortedByDescending { it.value.n }?.forEach { (k, v) -> if (k != "person" && k !in rows) add(v.name) }
    return rows.values.toList()
}

private fun builtInNote(id: String) = when (id) {
    AlertFilters.ALL -> "Every alert"
    AlertFilters.PEOPLE -> "People and faces"
    AlertFilters.VEHICLES -> "Cars, trucks and the like"
    AlertFilters.ANIMALS -> "Dogs, cats, wildlife"
    AlertFilters.FLAGGED -> "Alerts you flagged"
    else -> ""
}

@Composable
internal fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Sl.Muted, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ModeRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickableRow(onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, color = Sl.Text)
            Text(sub, color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun ToggleRow(title: String, sub: String, on: Boolean, onRemove: (() -> Unit)?, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, color = Sl.Text)
            if (sub.isNotEmpty()) Text(sub, color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove") }
        Switch(checked = on, onCheckedChange = { onToggle() })
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
