package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feerlessleadr.sightline.data.LogEntry
import com.feerlessleadr.sightline.data.LogFilter
import com.feerlessleadr.sightline.data.LogResult
import com.feerlessleadr.sightline.data.SystemLog
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.launch

private const val MAX_ROWS = 600

/** Blue Iris's own system log, newest first, with filters (All, Warnings & errors, Info) and a search. Blue Iris may keep it to admin users. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    firstFilter: LogFilter,
    load: suspend () -> LogResult?,
    onBack: () -> Unit,
) {
    var result by remember { mutableStateOf<LogResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadedAt by remember { mutableStateOf(0L) }
    var filter by remember { mutableStateOf(firstFilter) }
    var search by remember { mutableStateOf("") }
    val tick = remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    fun refresh() {
        if (loading && tick.intValue > 0) return
        tick.intValue++
        loading = true
        scope.launch {
            result = load()
            loadedAt = System.currentTimeMillis()
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System log") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { refresh() }, enabled = !loading) { Text("Refresh") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val r = result) {
                null -> Text(
                    if (loading) "Loading…" else "Can’t reach Blue Iris right now. Pull Refresh to try again.",
                    color = Sl.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
                )
                is LogResult.Denied -> Message(
                    "Blue Iris didn’t send its system log" + (if (r.reason.isNotEmpty()) " (${r.reason})" else "") +
                        ".\nIt may only show it to admin users: give this Blue Iris user admin rights, or look at the log in Blue Iris itself.",
                )
                is LogResult.Failed -> Message("Blue Iris didn’t send its system log" + (if (r.message.isNotEmpty()) " (${r.message})" else "") + ".")
                is LogResult.Ok -> {
                    val entries = r.entries
                    val shown = remember(entries, filter, search) { entries.filter { SystemLog.matches(it, filter, search) } }
                    val clock = remember(locale) { SimpleDateFormat("h:mm:ss a", locale) }
                    val full = remember(locale) { SimpleDateFormat("MMM d, h:mm:ss a", locale) }
                    Text(
                        "${entries.size} entr${if (entries.size == 1) "y" else "ies"}, newest first · loaded ${clock.format(Date(loadedAt))}",
                        color = Sl.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in LogFilter.entries) {
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} ${SystemLog.count(entries, f)}") })
                        }
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    if (shown.isEmpty()) {
                        Text(
                            if (entries.isEmpty()) "The log is empty." else "Nothing matches.",
                            color = Sl.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(shown.take(MAX_ROWS)) { e -> LogRow(e, whenText(e, clock, full)) }
                            if (shown.size > MAX_ROWS) {
                                item { Text("Showing the newest $MAX_ROWS of ${shown.size}.", color = Sl.Muted, modifier = Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun whenText(e: LogEntry, clock: SimpleDateFormat, full: SimpleDateFormat): String {
    val at = Date(e.dateSec * 1000)
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    return if (Instant.ofEpochSecond(e.dateSec).atZone(zone).toLocalDate() == today) clock.format(at) else full.format(at)
}

@Composable
private fun Message(text: String) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(text, color = Sl.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
    }
}

private fun levelColor(level: Int) = when (level) {
    1, 5 -> Color(0xFFF5D33B)
    2 -> Color(0xFFFF4D6A)
    3 -> Color(0xFFFF7A1A)
    4 -> Color(0xFF63C35B)
    10 -> Color(0xFFE8EDF2)
    0, 6 -> Color(0xFF4C8DFF)
    else -> Color(0xFF8B97A5)
}

private fun levelGlyph(level: Int) = when (level) {
    1, 3 -> "!"
    2 -> "×"
    4 -> "✓"
    5 -> "?"
    10 -> "☺"
    else -> "i"
}

@Composable
private fun LogRow(e: LogEntry, time: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 2.dp).size(20.dp).background(levelColor(e.level), CircleShape), contentAlignment = Alignment.Center) {
            Text(levelGlyph(e.level), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(e.source, color = Sl.Text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (e.count > 1) Text("×${e.count}", color = Sl.Muted, style = MaterialTheme.typography.labelSmall)
                Text(time, color = Sl.Muted, style = MaterialTheme.typography.labelSmall)
            }
            Text(e.message, color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
