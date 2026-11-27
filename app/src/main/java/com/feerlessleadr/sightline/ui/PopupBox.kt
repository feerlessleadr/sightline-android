package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.PopupEvent
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.StreamRequest
import com.feerlessleadr.sightline.video.cameraStreamRequest
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val POPUP_TOTAL_MS = 20_000

/**
 * A camera that pops up over the grid when something it was set to watch for is detected: large, with live video (or a
 * picture every second), for 20 s. Holding a finger on it pauses the countdown; a tap opens the camera; a newer detection
 * on the same camera starts the countdown again. The grid is the only screen that shows it, and it goes when the app does.
 */
@Composable
fun PopupBox(
    event: PopupEvent,
    camera: Camera,
    liveVideo: Boolean,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
    openVideo: suspend (id: String, size: StreamRequest?) -> VideoStream,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close by rememberUpdatedState(onClose)
    var paused by remember { mutableStateOf(false) }
    var leftMs by remember { mutableIntStateOf(POPUP_TOTAL_MS) }
    var videoOn by remember(camera.optionValue) { mutableStateOf(false) }
    var picture by remember(camera.optionValue) { mutableStateOf<ImageBitmap?>(null) }

    // Away from the screen the pop-up goes, as in the web app.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) close() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // A newer detection starts the 20 seconds again.
    LaunchedEffect(event.camera, event.atMs) {
        leftMs = POPUP_TOTAL_MS
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (leftMs > 0) {
                delay(250)
                if (!paused) leftMs -= 250
            }
            close()
        }
    }
    // A picture every second until (or unless) the video plays.
    LaunchedEffect(camera.optionValue, videoOn) {
        if (videoOn) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val bytes = loadStill(camera.optionValue, 960)
                if (bytes != null) {
                    val decoded = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    if (decoded != null) picture = decoded
                }
                delay(1_000)
            }
        }
    }

    val color = classColor(event.tag.cls)
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(event.atMs, locale) { SimpleDateFormat("h:mm:ss a", locale).format(Date(event.atMs)) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sl.Surface)
            .border(2.dp, color, RoundedCornerShape(12.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.tag.text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Text(camera.displayName, color = Sl.Text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
            Text(time, color = Sl.Muted, style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onClose) { Text("Close") }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .semantics {
                    contentDescription = "Picture from ${camera.displayName}"
                    role = Role.Button
                    onClick(label = "Open camera") { onOpen(); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { onOpen() },
                    )
                },
        ) {
            picture?.let { Image(it, contentDescription = camera.displayName, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize()) }
            if (liveVideo) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val ratio = minOf(2f, density.density)
                val widthDp = LocalConfiguration.current.screenWidthDp - 24
                val request = remember(camera.optionValue, widthDp) {
                    cameraStreamRequest(widthDp * ratio, widthDp * 9f / 16f * ratio, camera.width ?: 1920, camera.height ?: 1080)
                }
                OwnVideo(
                    cameraId = camera.optionValue,
                    request = request,
                    portrait = (camera.width ?: 1) < (camera.height ?: 0),
                    openVideo = openVideo,
                    onShowing = { videoOn = it },
                    onFailed = { videoOn = false },
                )
            }
        }
        LinearProgressIndicator(progress = { leftMs / POPUP_TOTAL_MS.toFloat() }, modifier = Modifier.fillMaxWidth(), color = color, trackColor = Sl.Surface2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpen) { Text("Open camera") }
        }
    }
}
