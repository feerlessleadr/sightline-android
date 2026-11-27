package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.sightline.data.AlertClips
import com.feerlessleadr.sightline.data.AlertFlags
import com.feerlessleadr.sightline.data.AlertItem
import com.feerlessleadr.sightline.data.AlertTags
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val FRAMES = 8
private const val STEP_MS = 220L
private const val HOLD_MS = 700L

/** Frames already fetched, by alert, so going back to a recent alert costs nothing more. */
private object PreviewFrames {
    val cache = LruCache<String, List<ImageBitmap?>>(6)
}

/**
 * The alert preview panel for wide screens: beside the list, the chosen alert plays as a slideshow of eight still pictures
 * spread across it (the web app's "Standard" preview, like UI3's mouse-over). The pictures load one at a time, so it is
 * light on Blue Iris. Touch the picture to open the alert in the player.
 */
@Composable
fun AlertPreviewPanel(
    alert: AlertItem?,
    flags: Long,
    cameraName: String,
    frame: suspend (camera: String, positionMs: Long) -> ByteArray?,
    thumbnail: suspend (path: String) -> ByteArray?,
    onOpen: (AlertItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alert == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Choose an alert to preview it", color = Sl.Muted, textAlign = TextAlign.Center)
        }
        return
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val locale = LocalConfiguration.current.locales[0]
    val duration = remember(alert) { AlertClips.durationMillis(alert.fileSize) }
    var start by remember(alert.path) { mutableStateOf<ImageBitmap?>(null) }
    var frames by remember(alert.path) { mutableStateOf(PreviewFrames.cache.get(alert.path) ?: List(FRAMES) { null }) }
    var shown by remember(alert.path) { mutableIntStateOf(-1) }

    // Until the first frame arrives, the list's own picture of the alert.
    LaunchedEffect(alert.path) {
        if (frames.any { it != null }) return@LaunchedEffect
        val bytes = thumbnail(alert.path) ?: return@LaunchedEffect
        start = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    // The frames, one at a time.
    LaunchedEffect(alert.path) {
        if (frames.all { it != null }) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val loaded = frames.toMutableList()
            for (i in 0 until FRAMES) {
                if (loaded[i] != null) continue
                val bytes = frame(alert.camera, alert.timeMs + duration * i / FRAMES)
                if (bytes != null) {
                    loaded[i] = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    frames = loaded.toList()
                }
            }
            if (loaded.any { it != null }) PreviewFrames.cache.put(alert.path, loaded.toList())
        }
    }
    // The slideshow: the next frame that has loaded, with a pause at the end of each loop.
    LaunchedEffect(alert.path) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var current = -1
            while (true) {
                delay(STEP_MS)
                val ok = frames.indices.filter { frames[it] != null }
                if (ok.isEmpty()) continue
                val next = ok.firstOrNull { it > current } ?: ok.first()
                if (next <= current) delay(HOLD_MS)
                current = next
                shown = next
            }
        }
    }

    val picture = frames.getOrNull(shown) ?: start
    val tags = remember(alert) { AlertTags.of(alert.memo, alert.flags) }
    val day = remember(alert, locale) { SimpleDateFormat("EEE, MMM d", locale).format(Date(alert.timeMs)) }
    val clock = SimpleDateFormat("h:mm:ss a", locale)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .semantics { contentDescription = "Preview of the alert from $cameraName"; role = Role.Button; onClick(label = "Open this alert") { onOpen(alert); true } }
                .clickable { onOpen(alert) },
        ) {
            picture?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            if (shown >= 0) {
                Text(
                    clock.format(Date(alert.timeMs + duration * shown / FRAMES)),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xA6000000)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(((shown + 1) / FRAMES.toFloat())).height(3.dp).background(Sl.Accent))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for (t in tags) {
                val color = classColor(t.cls)
                Text(t.text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp))
            }
            if (AlertFlags.has(flags, AlertFlags.FLAGGED)) GlyphIcon(Glyph.FlagOn, Sl.Accent, 16.dp)
            if (AlertFlags.has(flags, AlertFlags.PROTECTED)) GlyphIcon(Glyph.Lock, Sl.Accent, 16.dp)
        }
        Text(cameraName, style = MaterialTheme.typography.titleMedium, color = Sl.Text)
        Text("$day · ${clock.format(Date(alert.timeMs))} · ${durationText(duration)} long", color = Sl.Muted, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { onOpen(alert) }, modifier = Modifier.padding(top = 4.dp)) { Text("Open alert") }
    }
}
