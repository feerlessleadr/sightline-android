package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import com.feerlessleadr.sightline.data.AlertFilterSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feerlessleadr.sightline.data.AlertClips
import com.feerlessleadr.sightline.data.AlertFilters
import com.feerlessleadr.sightline.data.AlertFlags
import com.feerlessleadr.sightline.data.AlertItem
import com.feerlessleadr.sightline.data.AlertRange
import com.feerlessleadr.sightline.data.AlertTags
import com.feerlessleadr.sightline.data.AlertsEnd
import com.feerlessleadr.sightline.data.AlertsUi
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.Camera
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What the Alerts tab can ask for; built from the view model so the screen stays free of it. */
class AlertsActions(
    val ensureLoaded: () -> Unit,
    val reload: () -> Unit,
    val loadMore: () -> Unit,
    val select: (id: String, combine: Boolean) -> Unit,
    val setCamera: (String) -> Unit,
    val setRange: (AlertRange?) -> Unit,
    val lookFurtherBack: () -> Unit,
    val retry: () -> Unit,
    val thumbnail: suspend (path: String) -> ByteArray?,
    val open: (AlertItem) -> Unit,
    val changeFlags: suspend (path: String, before: Long, after: Long) -> Unit,
    val retainFilters: (allowed: List<String>) -> Unit,
    val filterSettings: AlertFilterSettings,
)

/** Thumbnails already loaded, and a limit on how many are asked for at once. */
private object Thumbnails {
    val cache = LruCache<String, ImageBitmap>(300)
    val gate = Semaphore(4)
}

private sealed interface AlertRow {
    data class Day(val text: String) : AlertRow
    data class Item(val alert: AlertItem) : AlertRow
}

/**
 * The Alerts tab: filter buttons (tap for one, long-press to combine), a camera choice and a time range, then the alerts
 * newest first under day headings. Tap an alert to watch it; long-press for flag and protect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsTab(
    ui: AlertsUi,
    groups: List<Camera>,
    cameras: List<Camera>,
    listState: LazyListState,
    actions: AlertsActions,
) {
    LaunchedEffect(Unit) {
        actions.ensureLoaded()
        actions.filterSettings.refreshIfStale()
    }
    val filterUi by actions.filterSettings.state.collectAsState()
    val chips = remember(filterUi.config) { filterUi.config.chips() }
    // A button that is no longer shown can't stay selected.
    LaunchedEffect(chips) { actions.retainFilters(chips) }
    var showFilters by remember { mutableStateOf(false) }
    val names = remember(groups, cameras) { (groups + cameras).associate { it.optionValue.lowercase() to it.displayName } }
    val nameOf: (String) -> String = { camera -> names[camera.lowercase()] ?: camera }
    var showRange by remember { mutableStateOf(false) }
    var markTarget by remember { mutableStateOf<AlertItem?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val loading by androidx.compose.runtime.rememberUpdatedState(ui.loading)
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(700)
            while (loading) delay(100)
            refreshing = false
        }
    }
    val rows = remember(ui.items) { dayRows(ui.items) }
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, ui.items.size, ui.loading, ui.exhausted) {
        if (nearEnd && ui.items.isNotEmpty() && !ui.loading && !ui.exhausted) actions.loadMore()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CameraChoice(ui.camera, nameOf(ui.camera), groups, cameras, actions.setCamera, Modifier.weight(1f))
            FilterPill(
                text = ui.range?.let { rangeText(it) } ?: "Any time",
                selected = ui.range != null,
                onClick = { showRange = true },
                onLongClick = {},
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips, key = { it }) { id ->
                val on = if (id == AlertFilters.ALL) ui.filters.isEmpty() else id in ui.filters
                val haptic = LocalHapticFeedback.current
                FilterPill(
                    text = AlertFilters.name(id),
                    selected = on,
                    onClick = { actions.select(id, false) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.select(id, true)
                    },
                )
            }
            item(key = "edit-filters") {
                TextButton(onClick = { showFilters = true }) { Text("Edit") }
            }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; actions.reload() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { row -> if (row is AlertRow.Item) "a" + row.alert.path else "d" + (row as AlertRow.Day).text }) { row ->
                    when (row) {
                        is AlertRow.Day -> Text(
                            row.text,
                            style = MaterialTheme.typography.titleSmall,
                            color = Sl.Muted,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        is AlertRow.Item -> AlertCard(
                            alert = row.alert,
                            flags = ui.flagsOf(row.alert),
                            cameraName = nameOf(row.alert.camera),
                            thumbnail = actions.thumbnail,
                            onClick = { actions.open(row.alert) },
                            onLongClick = { markTarget = row.alert },
                        )
                    }
                }
                item(key = "footer") { Footer(ui, actions) }
            }
        }
    }

    if (showRange) {
        RangeSheet(
            current = ui.range,
            onPick = { showRange = false; actions.setRange(it) },
            onDismiss = { showRange = false },
        )
    }
    if (showFilters) AlertFiltersSheet(filterUi, actions.filterSettings) { showFilters = false }
    markTarget?.let { alert ->
        MarkHost(alert, ui.flagsOf(alert), nameOf(alert.camera), actions.changeFlags) { markTarget = null }
    }
}

@Composable
private fun Footer(ui: AlertsUi, actions: AlertsActions) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val noun = AlertFilters.selectionNoun(ui.filters)
        when {
            ui.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            ui.end == AlertsEnd.Failed -> {
                Text(ui.error ?: "Could not load alerts.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = actions.retry) { Text("Retry") }
            }
            ui.end == AlertsEnd.RangeEnd -> Text(
                if (ui.items.isNotEmpty()) "End of the selected time range." else "No $noun for ${ui.range?.let { rangeText(it) }.orEmpty()}.",
                color = Sl.Muted,
                textAlign = TextAlign.Center,
            )
            ui.end == AlertsEnd.QuietWeek -> {
                Text(
                    if (ui.items.isNotEmpty()) "Nothing in the week before this." else "No $noun in the last 7 days.",
                    color = Sl.Muted,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = actions.lookFurtherBack) { Text("Look further back") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Sl.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) Sl.Accent else Sl.Line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Sl.Accent else Sl.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CameraChoice(current: String, currentName: String, groups: List<Camera>, cameras: List<Camera>, onPick: (String) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text(currentName, maxLines = 1, color = Sl.Text)
            Text("  ▾", color = Sl.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (g in groups) {
                DropdownMenuItem(
                    text = { Text(g.displayName, color = if (g.optionValue.equals(current, true)) Sl.Accent else Sl.Text) },
                    onClick = { open = false; onPick(g.optionValue) },
                )
            }
            for (c in cameras) {
                DropdownMenuItem(
                    text = { Text(c.displayName, color = if (c.optionValue.equals(current, true)) Sl.Accent else Sl.Text) },
                    onClick = { open = false; onPick(c.optionValue) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlertCard(
    alert: AlertItem,
    flags: Long,
    cameraName: String,
    thumbnail: suspend (String) -> ByteArray?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val tags = remember(alert) { AlertTags.of(alert.memo, alert.flags) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Sl.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(128.dp).height(76.dp).background(Sl.Surface2)) {
            AlertThumb(alert.path, thumbnail)
            Text(
                durationText(AlertClips.durationMillis(alert.fileSize)),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in tags) {
                    val color = classColor(t.cls)
                    Text(
                        t.text,
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(cameraName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(SimpleDateFormat("h:mm a", locale).format(Date(alert.timeMs)), style = MaterialTheme.typography.bodySmall, color = Sl.Muted)
                if (AlertFlags.has(flags, AlertFlags.FLAGGED)) GlyphIcon(Glyph.FlagOn, Sl.Accent, 14.dp)
                if (AlertFlags.has(flags, AlertFlags.PROTECTED)) GlyphIcon(Glyph.Lock, Sl.Accent, 14.dp)
            }
        }
    }
}

@Composable
private fun AlertThumb(path: String, loader: suspend (String) -> ByteArray?) {
    var picture by remember(path) { mutableStateOf(Thumbnails.cache.get(path)) }
    LaunchedEffect(path) {
        if (picture == null) {
            val bytes = Thumbnails.gate.withPermit { loader(path) }
            val decoded = bytes?.let { withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
            if (decoded != null) {
                Thumbnails.cache.put(path, decoded)
                picture = decoded
            }
        }
    }
    picture?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
}

/** Flag or protect an alert from the list (long-press). */
@Composable
private fun MarkHost(alert: AlertItem, flags: Long, cameraName: String, change: suspend (String, Long, Long) -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    fun toggle(bit: Int) {
        if (busy) return
        val want = !AlertFlags.has(flags, bit)
        busy = true
        scope.launch {
            try {
                change(alert.path, flags, AlertFlags.with(flags, bit, want))
                message = if (bit == AlertFlags.FLAGGED) {
                    if (want) "Flagged." else "Flag removed."
                } else {
                    if (want) "Protected: Blue Iris will keep this alert." else "No longer protected."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BlueIrisException) {
                message = AlertFlags.friendlyError(e.message)
            } finally {
                busy = false
            }
        }
    }
    MarkSheet(
        info = cameraName + " · " + SimpleDateFormat("EEE MMM d, h:mm a", locale).format(Date(alert.timeMs)),
        flagged = AlertFlags.has(flags, AlertFlags.FLAGGED),
        protectedAlert = AlertFlags.has(flags, AlertFlags.PROTECTED),
        busy = busy,
        message = message,
        onToggleFlag = { toggle(AlertFlags.FLAGGED) },
        onToggleProtect = { toggle(AlertFlags.PROTECTED) },
        onDownload = null,
        onDone = onDone,
    )
}

/** Quick time ranges (last hour, today, yesterday, last night) and a single day from a calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSheet(current: AlertRange?, onPick: (AlertRange?) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis() / 1000
    var pickDay by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sl.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            for ((label, range) in listOf(
                "Last hour" to AlertRange.lastHour(now),
                "Today" to AlertRange.today(now, zone),
                "Yesterday" to AlertRange.yesterday(now, zone),
                "Last night (8 PM to 6 AM)" to AlertRange.lastNight(now, zone),
            )) {
                TextButton(onClick = { onPick(range) }, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
            }
            TextButton(onClick = { pickDay = true }, modifier = Modifier.fillMaxWidth()) { Text("Pick a day and time…", modifier = Modifier.fillMaxWidth()) }
            if (current != null) {
                Button(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Any time") }
            }
        }
    }
    if (pickDay) {
        CustomRangeDialog(current, zone, now, onDismiss = { pickDay = false }, onPick = { pickDay = false; onPick(it) })
    }
}

/** A date with a From and To time, like the web app's range form. A To before From runs past midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(current: AlertRange?, zone: ZoneId, now: Long, onDismiss: () -> Unit, onPick: (AlertRange) -> Unit) {
    val base = Instant.ofEpochSecond(current?.startSec ?: now).atZone(zone)
    var date by remember { mutableStateOf(base.toLocalDate()) }
    var from by remember { mutableStateOf(if (current != null) base.hour * 60 + base.minute else 0) }
    var to by remember {
        mutableStateOf(
            current?.let { Instant.ofEpochSecond(it.endSec).atZone(zone).let { e -> e.hour * 60 + e.minute } } ?: (23 * 60 + 59),
        )
    }
    var step by remember { mutableStateOf(0) } // 0 the form, 1 date, 2 from, 3 to
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val clock = SimpleDateFormat("h:mm a", locale)
    fun timeText(minutes: Int) = clock.format(Date(java.time.LocalTime.of(minutes / 60, minutes % 60).atDate(date).atZone(zone).toInstant().toEpochMilli()))
    val dateText = SimpleDateFormat("EEE, MMM d, yyyy", locale).format(Date(date.atStartOfDay(zone).toInstant().toEpochMilli()))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show alerts from") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Date: $dateText", modifier = Modifier.fillMaxWidth()) }
                TextButton(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("From: ${timeText(from)}", modifier = Modifier.fillMaxWidth()) }
                TextButton(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) { Text("To: ${timeText(to)}", modifier = Modifier.fillMaxWidth()) }
                if (to <= from) Text("A To before From runs into the next day.", color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(AlertRange.window(date, from, to, zone)) }) { Text("Show alerts") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    when (step) {
        1 -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis() + 14 * 3_600_000L
                },
            )
            DatePickerDialog(
                onDismissRequest = { step = 0 },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        step = 0
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
            ) { DatePicker(state) }
        }
        2, 3 -> {
            val start = if (step == 2) from else to
            val tp = androidx.compose.material3.rememberTimePickerState(
                initialHour = start / 60,
                initialMinute = start % 60,
                is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current),
            )
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { step = 0 },
                title = { Text(if (step == 2) "From" else "To") },
                text = { androidx.compose.material3.TimePicker(tp) },
                confirmButton = {
                    TextButton(onClick = {
                        val m = tp.hour * 60 + tp.minute
                        if (step == 2) from = m else to = m
                        step = 0
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
            )
        }
    }
}

/** Day headings between the alerts (they are already newest first). */
private fun dayRows(items: List<AlertItem>): List<AlertRow> {
    val rows = ArrayList<AlertRow>()
    var last = ""
    for (a in items) {
        val day = dayLabel(a.timeMs)
        if (day != last) {
            last = day
            rows += AlertRow.Day(day)
        }
        rows += AlertRow.Item(a)
    }
    return rows
}

private fun dayLabel(ms: Long, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(ms))
    }
}

private fun rangeText(r: AlertRange): String {
    val zone = ZoneId.systemDefault()
    val a = Instant.ofEpochSecond(r.startSec).atZone(zone)
    val b = Instant.ofEpochSecond(r.endSec).atZone(zone)
    val clock = SimpleDateFormat("h:mm a", Locale.getDefault())
    val fullDay = a.hour == 0 && a.minute == 0 && b.hour == 23 && b.minute == 59 && a.toLocalDate() == b.toLocalDate()
    if (fullDay) return dayLabel(r.startSec * 1000)
    val sameDay = a.toLocalDate() == b.toLocalDate()
    return dayLabel(r.startSec * 1000) + " " + clock.format(Date(r.startSec * 1000)) + "–" +
        (if (sameDay) "" else dayLabel(r.endSec * 1000) + " ") + clock.format(Date(r.endSec * 1000))
}

private fun durationText(ms: Long): String {
    val s = Math.round(ms / 1000.0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
