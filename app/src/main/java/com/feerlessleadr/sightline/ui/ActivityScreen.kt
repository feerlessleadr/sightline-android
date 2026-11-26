package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feerlessleadr.sightline.data.ActivityFeed
import com.feerlessleadr.sightline.data.ActivityReport
import com.feerlessleadr.sightline.data.AlertClass
import com.feerlessleadr.sightline.data.AlertFilters
import com.feerlessleadr.sightline.data.Camera
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

private val TYPES = listOf(AlertFilters.ALL to "All", AlertFilters.PEOPLE to "People", AlertFilters.VEHICLES to "Vehicles", AlertFilters.ANIMALS to "Animals")

/** The kinds of alert from the bottom of a bar up. */
private val STACK = listOf(AlertClass.Motion, AlertClass.Delivery, AlertClass.Animal, AlertClass.Vehicle, AlertClass.Person)

/**
 * Activity: when and where alerts happen over the last 7, 14 or 30 days, from Blue Iris's alert list. Alerts per day stacked
 * by kind, and a map of camera by hour of day (brighter means more). Tap a camera to see its alerts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    feed: ActivityFeed,
    cameras: List<Camera>,
    onShowAlerts: (camera: String, type: String) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { feed.open() }
    val ui by feed.state.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val names = remember(cameras) { cameras.associate { it.optionValue.lowercase() to it.displayName } }
    fun nameOf(camera: String) = names[camera.lowercase()] ?: camera
    val hourFormat = remember(locale) { DateTimeFormatter.ofPattern("h a", locale) }
    fun hourText(h: Int) = hourFormat.format(LocalTime.of(h % 24, 0))
    val data = ui.data[ui.days]
    val report = remember(data, ui.type, ui.days) { data?.let { ActivityReport.build(it.items, ui.type, ui.days, System.currentTimeMillis(), ZoneId.systemDefault()) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Activity") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in listOf(7, 14, 30)) FilterChip(selected = ui.days == d, onClick = { feed.setDays(d) }, label = { Text("$d days") })
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((id, label) in TYPES) FilterChip(selected = ui.type == id, onClick = { feed.setType(id) }, label = { Text(label) })
            }
            if (report == null) {
                Text(
                    if (ui.loading) "Reading ${minOf(ui.days, ui.daysDone + 1)} of ${ui.days} days…" else "Reading the alert list…",
                    color = Sl.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
                return@Column
            }
            val what = if (ui.type == AlertFilters.ALL) "alerts" else TYPES.first { it.first == ui.type }.second.lowercase() + " alerts"
            val top = report.cameras.firstOrNull()
            Text(
                if (report.total > 0 && top != null) {
                    "${report.total} $what in the last ${ui.days} days · busiest camera: ${nameOf(top.camera)} (${top.total}) · busiest hour: ${hourText(report.busiestHour)}–${hourText(report.busiestHour + 1)} · quietest: ${hourText(report.quietestHour)}–${hourText(report.quietestHour + 1)}"
                } else {
                    "No $what in the last ${ui.days} days."
                },
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (ui.loading) Text("Reading ${minOf(ui.days, ui.daysDone + 1)} of ${ui.days} days…", color = Sl.Faint, style = MaterialTheme.typography.bodySmall)

            Title("Alerts per day")
            val maxDay = maxOf(1, report.days.maxOf { it.total })
            val step = if (ui.days > 14) 5 else if (ui.days > 7) 2 else 1
            val dayFormat = remember(locale, ui.days) { DateTimeFormatter.ofPattern(if (ui.days == 7) "EEE" else "M/d", locale) }
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val n = report.days.size
                val slot = size.width / n
                val barW = slot * 0.7f
                for ((i, day) in report.days.withIndex()) {
                    var bottom = size.height
                    for (cls in STACK) {
                        val c = day.byClass[cls] ?: continue
                        val h = size.height * c / maxDay
                        drawRect(classColor(cls), Offset(i * slot + (slot - barW) / 2, bottom - h), Size(barW, h))
                        bottom -= h
                    }
                    if (day.total == 0) drawRect(Sl.Surface2, Offset(i * slot + (slot - barW) / 2, size.height - 2.dp.toPx()), Size(barW, 2.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                for ((i, day) in report.days.withIndex()) {
                    val label = if ((report.days.size - 1 - i) % step == 0) {
                        java.time.Instant.ofEpochMilli(day.startMs).atZone(ZoneId.systemDefault()).let { dayFormat.format(it) }
                    } else {
                        ""
                    }
                    Text(label, color = Sl.Muted, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                }
            }

            Title("By camera and time of day")
            if (report.total == 0) {
                Text("No alerts in this period.", color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(NAME_W))
                    for (h in 0 until 24) {
                        Text(if (h % 6 == 0) hourText(h).replace(" ", "").lowercase() else "", color = Sl.Faint, fontSize = 8.sp, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                    }
                    Box(Modifier.width(TOTAL_W))
                }
                HeatRow("All cameras", report.hours, report.total, report.hours.max(), { onShowAlerts("index", ui.type) }, bold = true)
                val maxCell = maxOf(1, report.cameras.maxOf { c -> c.hours.max() })
                for (c in report.cameras) HeatRow(nameOf(c.camera), c.hours, c.total, maxCell, { onShowAlerts(c.camera, ui.type) })
            }
            Text(
                "Brighter means more alerts. Tap a camera to see its alerts. Times are this phone’s time zone.",
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
    }
}

private val NAME_W = 92.dp
private val TOTAL_W = 34.dp

@Composable
private fun Title(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun HeatRow(name: String, hours: IntArray, total: Int, max: Int, onClick: () -> Unit, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = if (bold) Sl.Accent else Sl.Text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(NAME_W).padding(end = 4.dp))
        for (n in hours) {
            val alpha = if (n > 0) 0.12f + 0.88f * sqrt(n.toFloat() / maxOf(1, max)) else 0f
            Box(
                Modifier.weight(1f).height(18.dp).padding(horizontal = 0.5.dp).background(if (n > 0) Sl.Accent.copy(alpha = alpha) else Sl.Surface2),
            )
        }
        Text(total.toString(), color = Sl.Muted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(TOTAL_W))
    }
}
