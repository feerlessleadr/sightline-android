package com.feerlessleadr.sightline.ui

import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.feerlessleadr.sightline.data.BlueIrisException
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.StreamRequest
import com.feerlessleadr.sightline.video.playLive
import com.feerlessleadr.sightline.video.playerStreamRequest
import kotlinx.coroutines.CancellationException

/**
 * Full-width live video of one camera. The stream runs only while the video surface exists, so leaving
 * the screen or the app stops it, and coming back starts it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVideoScreen(
    camera: Camera,
    quality: VideoQuality,
    openVideo: suspend (String, StreamRequest?) -> VideoStream,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
    onQuality: (VideoQuality) -> Unit,
    onBack: () -> Unit,
) {
    var surface by remember { mutableStateOf<Surface?>(null) }
    var status by remember { mutableStateOf("Connecting…") }
    var error by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var aspect by remember(camera.optionValue) { mutableStateOf((camera.width ?: 16).toFloat() / (camera.height ?: 9).toFloat()) }
    var preview by remember(camera.optionValue) { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pickQuality by remember { mutableStateOf(false) }
    val metrics = LocalResources.current.displayMetrics

    LaunchedEffect(surface, attempt, camera.optionValue, quality) {
        val target = surface ?: return@LaunchedEffect
        status = "Connecting…"
        error = null
        stats = ""
        playing = false
        try {
            val cw = camera.width ?: 1920
            val ch = camera.height ?: 1080
            val size = playerStreamRequest(
                metrics.widthPixels / metrics.density, metrics.heightPixels / metrics.density, metrics.density, cw, ch, quality,
            )
            val started = SystemClock.elapsedRealtime()
            // A current still fills the screen while the video connects (Blue Iris takes a few seconds to start a stream).
            launch {
                val bytes = loadStill(camera.optionValue, 960) ?: return@launch
                val decoded = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                if (decoded != null && !playing) preview = decoded
            }
            val stream = openVideo(camera.optionValue, size)
            val connected = SystemClock.elapsedRealtime() - started
            status = "Waiting for the first picture…"
            var firstPicture = -1L
            playLive(
                stream, target,
                onSize = { w, h -> aspect = w.toFloat() / h.toFloat() },
                onStats = { s ->
                    if (s.shown > 0 && firstPicture < 0) firstPicture = SystemClock.elapsedRealtime() - started
                    stats = "$s; asked for ${size.width}x${size.height} ${size.kbps} kbps; connection opened after $connected ms, first picture ${firstPicture} ms after the request started"
                    if (s.shown > 0) {
                        status = ""
                        playing = true
                        preview = null
                    }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlueIrisException) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(camera.displayName) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = { pickQuality = true }) { Text(quality.label.uppercase()) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().aspectRatio(aspect).background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        SurfaceView(context).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    surface = holder.surface
                                }

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    surface = null
                                }
                            })
                        }
                    },
                )
                // Drawn only until the first video frame, because it would hide the video underneath.
                if (!playing) {
                    preview?.let {
                        Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
                if (status.isNotEmpty() && error == null) {
                    Text(
                        status,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (pickQuality) {
                ChoiceDialog(
                    title = "Video quality",
                    options = VideoQuality.entries,
                    selected = quality,
                    label = { "${it.label} · ${it.note}" },
                    onPick = { onQuality(it); pickQuality = false },
                    onDismiss = { pickQuality = false },
                )
            }
            if (stats.isNotEmpty()) {
                // Temporary diagnostics while live video is being proven on real phones.
                Text(stats, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { attempt++ }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Retry") }
            }
        }
    }
}
