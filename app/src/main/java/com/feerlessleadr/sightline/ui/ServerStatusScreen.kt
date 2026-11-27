package com.feerlessleadr.sightline.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.DiskInfo
import com.feerlessleadr.sightline.data.ServerNumbers
import com.feerlessleadr.sightline.data.Retention
import com.feerlessleadr.sightline.data.RetentionFeed
import androidx.compose.runtime.collectAsState
import com.feerlessleadr.sightline.data.ServerStatus
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Server status, like the web app's: Blue Iris's CPU, memory, recording space, frame rate, uptime, web connections,
 * warnings and profile, the disks it records to, and each camera's frame rate and bit rate. Refreshed every 2 s while open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatusScreen(
    serverName: String?,
    version: String?,
    load: suspend () -> Pair<ServerStatus, List<Camera>>?,
    retention: RetentionFeed,
    onOpenLog: () -> Unit,
    onBack: () -> Unit,
) {
    var status by remember { mutableStateOf<ServerStatus?>(null) }
    var cameras by remember { mutableStateOf<List<Camera>>(emptyList()) }
    var live by remember { mutableStateOf(true) }
    val ret by retention.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // How far back each camera goes: one long request per camera, started once the camera list is in (not again within 10 minutes).
    LaunchedEffect(cameras.isNotEmpty()) {
        if (cameras.isNotEmpty()) retention.refresh(ServerNumbers.rates(cameras).filter { it.state != "disabled" }.map { it.id })
    }
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val result = load()
                live = result != null
                if (result != null) {
                    status = result.first
                    cameras = result.second
                }
                delay(2_000)
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Server status")
                        Box(Modifier.size(9.dp).background(if (live) Color(0xFF3DDC84) else Sl.Faint, CircleShape))
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).readable().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Text(
                listOfNotNull(serverName, version?.let { "Blue Iris $it" }).joinToString(" · "),
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val s = status
            if (s == null) {
                Text(
                    if (live) "Loading…" else "Can’t reach Blue Iris right now. Trying again.",
                    color = Sl.Muted,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                )
                return@Column
            }
            val rows = remember(cameras) { ServerNumbers.rates(cameras) }
            val running = rows.filter { it.state != "disabled" }
            val fps = running.sumOf { it.fps }
            val mbps = running.sumOf { it.mbps }
            val used = s.disks.sumOf { it.used }
            val allocated = s.disks.sumOf { it.allocated }
            val uptime = ServerNumbers.uptime(s.uptime)
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            val tiles = listOf(
                Tile("CPU", s.cpu?.let { "${Math.round(it)}%" } ?: "?", s.gpu?.let { "GPU ${Math.round(it)}%" } ?: "GPU not reported", s.cpu),
                Tile(
                    "Memory",
                    s.memory?.let { ServerNumbers.biSize(it) } ?: "?",
                    s.memoryLoad?.let { "Computer ${Math.round(it)}% of ${ServerNumbers.biSize(s.physicalMemory)}" } ?: "Used by Blue Iris",
                    s.memoryLoad,
                ),
                Tile(
                    "Recordings",
                    if (allocated > 0) "${Math.round(used * 100.0 / allocated)}%" else if (used > 0) ServerNumbers.mib(used) else "?",
                    if (allocated > 0) "${ServerNumbers.mib(used)} / ${ServerNumbers.mib(allocated)} allowed" else "Space used",
                    if (allocated > 0) used * 100.0 / allocated else null,
                ),
                Tile("Frame rate", "${Math.round(fps)} fps", "${running.size} camera${if (running.size == 1) "" else "s"} · ${String.format(locale, "%.1f", mbps)} Mbps", null),
                Tile(
                    "Uptime",
                    uptime.first,
                    if (uptime.second > 0) "Since " + SimpleDateFormat("MMM d, h:mm a", locale).format(Date(System.currentTimeMillis() - uptime.second)) else "",
                    null,
                ),
                Tile("Connections", s.connections?.toString() ?: "?", "To the web server", null),
                Tile("Warnings", s.warnings.toString(), if (s.warnings > 0) "Open the system log" else "Nothing new", null, warn = s.warnings > 0, onClick = onOpenLog),
                Tile(
                    "Footage kept",
                    Retention.range(ret.byCamera.values, System.currentTimeMillis()) ?: if (ret.busy) "…" else "?",
                    if (ret.byCamera.size > 1) "Range across cameras" else if (ret.byCamera.isEmpty()) "Checking each camera" else "Oldest recording",
                    null,
                ),
                Tile(
                    "Profile",
                    s.profile ?: "?",
                    listOfNotNull(
                        s.schedule?.let { "Schedule $it" },
                        if (s.locked) "held" else null,
                        s.signal?.takeIf { it != "green" }?.let { "signal $it" },
                    ).joinToString(" · "),
                    null,
                ),
            )
            for (pair in tiles.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in pair) TileCard(t, Modifier.weight(1f))
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }

            SectionTitle("Disk usage")
            if (s.disks.isEmpty()) Text("Blue Iris didn’t report any disks.", color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
            for (disk in s.disks) DiskCard(disk)
            s.clips?.let { Text(it, color = Sl.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }

            SectionTitle("Cameras")
            if (rows.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Camera", color = Sl.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text("fps", color = Sl.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                    Text("Mbps", color = Sl.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                    Text("Stored", color = Sl.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
                }
            }
            for (c in rows) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, color = if (c.state.isEmpty()) Sl.Text else Sl.Faint, modifier = Modifier.weight(1f), maxLines = 1)
                    if (c.state.isNotEmpty()) {
                        Text(c.state, color = Sl.Faint, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(String.format(locale, "%.1f", c.fps), color = Sl.Text, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                        Text(String.format(locale, "%.2f", c.mbps), color = Sl.Text, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                        val kept = ret.byCamera[c.id]
                        Text(
                            when {
                                kept == null -> if (ret.busy) "…" else ""
                                kept.none -> "none"
                                else -> Retention.kept(System.currentTimeMillis() - kept.fromMs) + if (kept.older) "*" else ""
                            },
                            color = Sl.Text,
                            modifier = Modifier.width(72.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            Text(
                "Stored: how far back each camera’s recordings go, counted back from today until a gap of more than 2 days. Checked when this opens (every 10 minutes at most)." +
                    if (ret.byCamera.values.any { it.older }) " * Also has older recordings after a gap, often protected clips." else "",
                color = Sl.Muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(Modifier.height(24.dp))
        }
    }
}

private class Tile(val key: String, val value: String, val sub: String, val bar: Double?, val warn: Boolean = false, val onClick: (() -> Unit)? = null)

@Composable
private fun TileCard(t: Tile, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Sl.Surface2).then(if (t.onClick != null) Modifier.clickable(onClick = t.onClick) else Modifier).padding(12.dp)) {
        Text(t.key, color = Sl.Muted, style = MaterialTheme.typography.labelMedium)
        Text(t.value, color = if (t.warn) Sl.Live else Sl.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        if (t.sub.isNotEmpty()) Text(t.sub, color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
        t.bar?.let { v ->
            val color = if (v >= 90) Sl.Live else if (v >= 75) Color(0xFFE0A030) else Sl.Accent
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Sl.Surface)) {
                Box(Modifier.fillMaxWidth((v / 100.0).coerceIn(0.0, 1.0).toFloat()).height(5.dp).background(color))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun DiskCard(disk: DiskInfo) {
    val seg = disk.segments()
    val recColor = Sl.Accent
    val overColor = Sl.Live
    val allocColor = Sl.Accent.copy(alpha = 0.35f)
    val otherColor = Sl.Faint
    val freeColor = Sl.Surface
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(Sl.Surface2).padding(12.dp)) {
        Text(disk.name.ifEmpty { "Disk" }, color = Sl.Text, fontWeight = FontWeight.SemiBold)
        Text(
            (if (seg.monitored) "${ServerNumbers.mib(disk.free)} free of ${ServerNumbers.mib(seg.capacity)}" else "Free space not monitored") +
                " · " + (if (disk.allocated > 0 || disk.used > 0) "${ServerNumbers.mib(disk.allocated)} allocated" else "not used for recordings"),
            color = Sl.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).background(freeColor)) {
            for ((value, color) in listOf(seg.recordings to recColor, seg.overAllocation to overColor, seg.allocatedFree to allocColor, seg.other to otherColor, seg.free to freeColor)) {
                if (value > 0) Box(Modifier.weight((value.toDouble() / seg.whole).coerceAtLeast(0.001).toFloat()).fillMaxSize().background(color))
            }
            // Whatever the bar does not cover (a disk that reports no total) stays the empty colour.
            val covered = (seg.recordings + seg.overAllocation + seg.allocatedFree + seg.other + seg.free).toDouble() / seg.whole
            if (covered < 1.0) Box(Modifier.weight((1.0 - covered).toFloat().coerceAtLeast(0.001f)).fillMaxSize())
        }
        val legend = buildList {
            if (disk.allocated > 0 || disk.used > 0) add("Recordings ${ServerNumbers.mib(seg.recordings)}" to recColor)
            if (seg.overAllocation > 0) add("Over allocation ${ServerNumbers.mib(seg.overAllocation)}" to overColor)
            if (seg.allocatedFree > 0) add("Allocated, free ${ServerNumbers.mib(seg.allocatedFree)}" to allocColor)
            if (seg.monitored) add("Other files ${ServerNumbers.mib(seg.other)}" to otherColor)
            if (seg.monitored) add("Free ${ServerNumbers.mib(seg.free)}" to Color(0xFF666B75))
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((text, color) in legend) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text(text, color = Sl.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (seg.note.isNotEmpty()) Text(seg.note, color = Sl.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}
