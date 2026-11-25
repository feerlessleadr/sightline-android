package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import android.view.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.GroupGlView
import com.feerlessleadr.sightline.video.GroupLayout
import com.feerlessleadr.sightline.video.StreamRequest
import com.feerlessleadr.sightline.video.TileSpec
import com.feerlessleadr.sightline.video.groupStreamRequest
import com.feerlessleadr.sightline.video.playLive
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF6FDC8C)
private val Red = Color(0xFFFF4D4D)
private val Grey = Color(0xFF5D6875)

private const val STILL_REFRESH_MS = 1_500L
private const val STILL_WIDTH = 480
private const val NO_PICTURE_MS = 10_000L

/**
 * The camera grid. With live video on, ONE stream of the whole Blue Iris [group] is decoded once and each tile
 * draws its own camera's part of that picture (the way the web app does it). Tiles whose camera is not in the
 * group picture, and every tile if the stream cannot play, show a still that refreshes every 1.5 s instead.
 */
@Composable
fun LiveGrid(
    group: Camera?,
    cameras: List<Camera>,
    tileSize: TileSize,
    liveVideo: Boolean,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
    openVideo: suspend (id: String, size: StreamRequest?) -> VideoStream,
    onOpenCamera: (Camera) -> Unit,
    onDiagnostics: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // What Blue Iris says about where the cameras sit in the group picture (from the camera list).
    val listLayout = remember(group) {
        val names = group?.group
        val rects = group?.rects
        val w = group?.width ?: 0
        val h = group?.height ?: 0
        if (names != null && rects != null && names.size == rects.size && w > 0 && h > 0) GroupLayout(names, rects, w, h) else null
    }
    var headerLayout by remember(group?.optionValue) { mutableStateOf<GroupLayout?>(null) }
    val layout = headerLayout ?: listLayout

    val glView = remember { GroupGlView(context) }
    var decoderSurface by remember { mutableStateOf<Surface?>(null) }
    var liveNow by remember(group?.optionValue) { mutableStateOf(false) }
    var failure by remember(group?.optionValue) { mutableStateOf<String?>(null) }

    DisposableEffect(glView, lifecycle) {
        glView.onSurface = { decoderSurface = it }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> glView.onResume()
                Lifecycle.Event.ON_STOP -> {
                    decoderSurface = null // stops the stream; a new surface comes with the next start
                    glView.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) glView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            glView.onSurface = null
            glView.onPause()
            glView.release()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { 4.dp.toPx() }
        val grid = remember(cameras.size, widthPx, heightPx, tileSize) {
            layoutTiles(cameras.size, widthPx, heightPx, tileSize, gapPx, minTileWidth = with(density) { 110.dp.toPx() })
        }
        val scroll = rememberScrollState()

        val groupId = group?.optionValue
        val wantLive = liveVideo && groupId != null && layout != null
        // Sized from the camera list's group size only, so a layout announced by the stream never restarts it.
        val request = remember(widthPx, listLayout) { listLayout?.let { groupStreamRequest(widthPx, it.width, it.height) } }

        // One stream for the whole group, only while the grid is on screen (the effect leaves with it).
        LaunchedEffect(groupId, decoderSurface, request, wantLive) {
            val surface = decoderSurface
            if (!wantLive || surface == null || request == null) {
                liveNow = false
                return@LaunchedEffect
            }
            var fails = 0
            while (true) {
                var picture = false
                var problem: String? = null
                try {
                    coroutineScope {
                        val stream = openVideo(groupId, request)
                        val player = launch {
                            playLive(
                                stream, surface,
                                onSize = { w, h ->
                                    glView.setVideoSize(w, h)
                                    headerLayout = GroupLayout.fromHeaders(stream.camListHeader, stream.recListHeader, w, h)
                                },
                                onStats = { s ->
                                    if (s.shown > 0 && !picture) { picture = true; liveNow = true; failure = null }
                                    onDiagnostics("live group: $s")
                                },
                            )
                        }
                        val watchdog = launch {
                            delay(NO_PICTURE_MS)
                            if (!picture) player.cancel()
                        }
                        player.join()
                        watchdog.cancel()
                        if (!picture) problem = "no picture after 10 s"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: BlueIrisException) {
                    problem = e.message
                }
                liveNow = false
                if (picture) fails = 0
                fails++
                failure = problem ?: "the stream stopped"
                onDiagnostics("live group stopped: $failure; stills until it retries")
                delay(minOf(300_000L, 15_000L * fails))
            }
        }

        // Tell the GL view where every live tile is and which part of the picture it shows.
        val tileSpecs = remember(grid, layout, liveNow, cameras, scroll.value) {
            if (!liveNow || layout == null) {
                emptyList()
            } else {
                cameras.mapIndexedNotNull { i, cam ->
                    val frac = layout.fractionFor(cam.optionValue) ?: return@mapIndexedNotNull null
                    val box = grid.boxes[i]
                    val portrait = (cam.width ?: 1) < (cam.height ?: 0)
                    TileSpec(box.x.roundToInt(), (box.y - scroll.value).roundToInt(), box.w.roundToInt(), box.h.roundToInt(), frac, portrait)
                }
            }
        }
        LaunchedEffect(tileSpecs) { glView.setTiles(tileSpecs) }

        if (wantLive) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(with(density) { grid.contentHeight.toDp() })) {
                cameras.forEachIndexed { i, cam ->
                    val box = grid.boxes[i]
                    val inPicture = liveNow && layout?.fractionFor(cam.optionValue) != null
                    CameraTile(
                        cam = cam,
                        modifier = Modifier
                            .offset { IntOffset(box.x.roundToInt(), box.y.roundToInt()) }
                            .size(with(density) { box.w.toDp() }, with(density) { box.h.toDp() }),
                        useStill = !inPicture,
                        loadStill = loadStill,
                        onClick = { onOpenCamera(cam) },
                    )
                }
            }
        }
        failure?.let {
            Text(
                "Live video unavailable ($it). Showing stills.",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
            )
        }
    }
}

/**
 * One camera tile: transparent over the shared live picture ([useStill] false), otherwise a still that
 * refreshes every 1.5 s while the tile is on screen and the app is in the foreground.
 */
@Composable
private fun CameraTile(
    cam: Camera,
    modifier: Modifier,
    useStill: Boolean,
    loadStill: suspend (String, Int) -> ByteArray?,
    onClick: () -> Unit,
) {
    val (color, note) = when {
        !cam.online -> Grey to "No signal"
        cam.isTriggered || cam.isAlerting -> Red to "Triggered"
        cam.isMotion -> Color.White to "Motion"
        else -> Green to ""
    }
    var picture by remember(cam.optionValue) { mutableStateOf<ImageBitmap?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cam.optionValue, useStill) {
        if (!useStill) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val bytes = loadStill(cam.optionValue, STILL_WIDTH)
                if (bytes != null) {
                    val decoded = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    if (decoded != null) picture = decoded
                }
                delay(STILL_REFRESH_MS)
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (useStill) Modifier.background(Color.Black) else Modifier)
            .clickable(onClick = onClick),
    ) {
        if (useStill) {
            picture?.let {
                Image(it, contentDescription = cam.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(cam.displayName, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
            if (note.isNotEmpty()) Text(note, color = Color.White, style = MaterialTheme.typography.labelSmall)
            if (cam.ptz) Text("PTZ", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}
