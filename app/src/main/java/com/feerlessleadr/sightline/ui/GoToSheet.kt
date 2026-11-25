package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.Timeline
import com.feerlessleadr.sightline.video.CalendarMath
import com.feerlessleadr.sightline.video.MonthInfo
import com.feerlessleadr.sightline.video.TimelineMath
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Calendar
import kotlinx.coroutines.CancellationException

/**
 * "Go to date and time": quick choices, or a calendar and a from/to time. The calendar greys out days with no
 * recordings for this camera and days still to come, and puts a dot on days with alerts; it asks the timeline for one
 * month at a time and keeps each answer in [cache] for a while.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoToSheet(
    cameraName: String,
    defaultAt: Long,
    cache: MutableMap<YearMonth, MonthInfo>,
    loadMonth: suspend (startMs: Long, endMs: Long) -> Timeline,
    onGo: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val at = remember { Instant.ofEpochMilli(defaultAt).atZone(zone) }
    var month by remember { mutableStateOf(YearMonth.from(at)) }
    var selected by remember { mutableStateOf(at.toLocalDate()) }
    val defaultFrom = remember { ((at.hour * 60 + at.minute) / 5 * 5 - 30).coerceAtLeast(0) }
    var fromMin by remember { mutableIntStateOf(defaultFrom) }
    var toMin by remember { mutableIntStateOf((defaultFrom + 60).coerceAtMost(23 * 60 + 59)) }
    var editing by remember { mutableStateOf<Int?>(null) } // 0 = from, 1 = to
    var info by remember(month) { mutableStateOf<MonthInfo?>(null) }
    var loading by remember(month) { mutableStateOf(false) }
    var failed by remember(month) { mutableStateOf(false) }

    LaunchedEffect(month) {
        val now = System.currentTimeMillis()
        val cached = cache[month]
        if (cached != null && CalendarMath.isFresh(cached, month, now, zone)) {
            info = cached
            return@LaunchedEffect
        }
        loading = true
        try {
            val (start, end) = CalendarMath.monthRange(month, now, zone)
            val result = if (end > start) loadMonth(start, end) else Timeline.Empty
            val summary = CalendarMath.summarise(result.clips, result.alerts, month, zone, now)
            cache[month] = summary
            info = summary
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlueIrisException) {
            failed = true
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Sl.Surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("Go to date and time", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
            val dayStart = remember {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimelineMath.QuickRange.entries.forEach { kind ->
                    Pill(kind.label) {
                        val (s, e) = TimelineMath.quickRange(kind, System.currentTimeMillis(), dayStart)
                        onGo(s, e)
                    }
                }
            }

            // Month heading with the arrows.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", fontSize = 22.sp) }
                Text(
                    month.month.getDisplayName(TextStyle.FULL, locale) + " " + month.year,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.from(today)) { Text("›", fontSize = 22.sp) }
            }
            // Weekday letters, Sunday first like the web app.
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val letter = java.time.DayOfWeek.of(if (i == 0) 7 else i).getDisplayName(TextStyle.NARROW, locale)
                    Text(letter, color = Sl.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            val cells: List<Int?> = List(CalendarMath.leadingBlanks(month)) { null } + (1..month.lengthOfMonth()).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val day = week.getOrNull(i)
                        if (day == null) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(day)
                            val disabled = CalendarMath.isDisabled(date, today, info)
                            DayCell(
                                day = day,
                                selected = date == selected,
                                isToday = date == today,
                                hasAlerts = info?.alertDays?.contains(day) == true && !disabled,
                                disabled = disabled,
                                modifier = Modifier.weight(1f),
                            ) { selected = date }
                        }
                    }
                }
            }
            Text(
                when {
                    loading -> "Checking which days have recordings…"
                    failed -> "Couldn't check which days have recordings."
                    info != null && info!!.recorded.isEmpty() -> "No recordings for $cameraName this month."
                    info != null -> "Greyed-out days have no recordings for $cameraName. A dot marks days with alerts."
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = Sl.Muted,
                modifier = Modifier.padding(top = 6.dp),
            )

            // From and to times, then go.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From", color = Sl.Muted)
                Pill(formatMinutes(fromMin)) { editing = 0 }
                Text("to", color = Sl.Muted)
                Pill(formatMinutes(toMin)) { editing = 1 }
            }
            val canGo = !CalendarMath.isDisabled(selected, today, info)
            Button(
                onClick = {
                    val (s, e) = CalendarMath.range(selected, fromMin, toMin, zone)
                    onGo(s, e)
                },
                enabled = canGo,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Go to ${selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}") }
        }
    }

    editing?.let { which ->
        val start = if (which == 0) fromMin else toMin
        val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (which == 0) "Start time" else "End time") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = state.hour * 60 + state.minute
                    if (which == 0) fromMin = minutes else toMin = minutes
                    editing = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayCell(
    day: Int,
    selected: Boolean,
    isToday: Boolean,
    hasAlerts: Boolean,
    disabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(modifier.aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .then(if (isToday && !selected) Modifier.border(1.dp, Sl.Accent, CircleShape) else Modifier)
                .background(if (selected) Sl.Accent else Color.Transparent)
                .then(if (disabled) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(),
                fontSize = 14.sp,
                color = when {
                    selected -> Color(0xFF00212F)
                    disabled -> Sl.Faint.copy(alpha = 0.5f)
                    else -> Sl.Text
                },
            )
            if (hasAlerts) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(4.dp)
                        .background(if (selected) Color(0xFF00212F) else Sl.Person, CircleShape),
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
