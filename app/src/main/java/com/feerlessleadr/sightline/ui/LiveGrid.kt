package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import android.view.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.foundation.border
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import com.feerlessleadr.sightline.video.GraySample
import com.feerlessleadr.sightline.video.MotionDetector
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.feerlessleadr.sightline.data.AlertClass
import com.feerlessleadr.sightline.data.CameraTag
import com.feerlessleadr.sightline.data.isFresh
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.GroupGlView
import com.feerlessleadr.sightline.video.GroupLayout
import com.feerlessleadr.sightline.video.StreamRequest
import com.feerlessleadr.sightline.video.TileSpec
import com.feerlessleadr.sightline.video.cameraStreamRequest
import com.feerlessleadr.sightline.video.groupStreamRequest
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableStateMapOf
import com.feerlessleadr.sightline.video.playLive
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF63C35B) // the web app's live dot
private val Red = Sl.Live
private val Grey = Sl.Faint

private const val STILL_REFRESH_MS = 1_500L
private const val STILL_WIDTH = 480
private const val NO_PICTURE_MS = 10_000L
private const val MAX_OWN_STREAMS = 4
private const val STRETCH_LIMIT = 2f

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
    arrangement: GroupArrangement,
    onArrangement: (GroupArrangement) -> Unit,
    onResetLayout: () -> Unit,
    highlight: Boolean,
    aiLabels: Boolean,
    aiTags: Map<String, CameraTag>,
    nowMs: Long,
    motionBoxes: Boolean,
    motionSensitivity: Int,
    liveVideo: Boolean,
    loadStill: suspend (camera: String, width: Int) -> ByteArray?,
    openVideo: suspend (id: String, size: StreamRequest?) -> VideoStream,
    onOpenCamera: (Camera) -> Unit,
    onDiagnostics: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // While a tile is being dragged the new order lives here only (saved once on release), so the grid does not
    // re-flow through the saved settings on every swap, which made tiles jump back and forth.
    var dragArrangement by remember { mutableStateOf<GroupArrangement?>(null) }
    val effective = dragArrangement ?: arrangement
    val ordered = remember(cameras, effective) { effective.sorted(cameras) { it.optionValue } }
    val shapes = remember(ordered, effective) { ordered.map { effective.shapes[it.optionValue] ?: TileShape.Normal } }
    // Set after a swap and cleared once the grid has been laid out again, so one drag movement swaps at most once.
    val swapPending = remember { booleanArrayOf(false) }
    LaunchedEffect(ordered) { swapPending[0] = false }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var sheetFor by remember { mutableStateOf<Camera?>(null) }
    // Big tiles with their own stream: when one may try again after a failure, how often it failed, whether it shows a picture.
    val ownBlocked = remember { mutableStateMapOf<String, Long>() }
    val ownFails = remember { HashMap<String, Int>() }
    val ownShowing = remember { mutableStateMapOf<String, Boolean>() }
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

    // Motion boxes: one detector per camera, fed by stills or by small samples of the shared live picture.
    val sensitivity by rememberUpdatedState(motionSensitivity)
    val detectors = remember { HashMap<String, MotionDetector>() }
    val boxTick = remember { mutableLongStateOf(0L) }
    val detectorFor = { id: String -> detectors.getOrPut(id) { MotionDetector { sensitivity } } }
    LaunchedEffect(motionBoxes) {
        if (!motionBoxes) detectors.clear()
        while (motionBoxes) { // keeps fading boxes redrawn
            delay(250)
            boxTick.longValue = SystemClock.uptimeMillis()
        }
    }

    val glView = remember { GroupGlView(context) }
    var decoderSurface by remember { mutableStateOf<Surface?>(null) }
    var liveNow by remember(group?.optionValue) { mutableStateOf(false) }
    var failure by remember(group?.optionValue) { mutableStateOf<String?>(null) }

    DisposableEffect(glView, lifecycle) {
        glView.onSurface = { decoderSurface = it }
        glView.onSamples = { samples ->
            val now = SystemClock.uptimeMillis()
            for ((id, sample) in samples) detectorFor(id).update(sample, "live", now)
            boxTick.longValue = now
        }
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
            glView.onSamples = null
            glView.onPause()
            glView.release()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val gapPx = with(density) { 4.dp.toPx() }
        val phone = maxWidth < 600.dp
        val grid = remember(ordered.size, widthPx, heightPx, tileSize, shapes, phone) {
            layoutTiles(ordered.size, widthPx, heightPx, tileSize, gapPx, minTileWidth = with(density) { 110.dp.toPx() }, shapes = shapes, fullWidthBig = phone)
        }
        val currentGrid by rememberUpdatedState(grid)
        val currentOrdered by rememberUpdatedState(ordered)
        val currentArrangement by rememberUpdatedState(effective)
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

        // Up to three big tiles (Large, Wide, Tall) that are on screen get the camera's own stream, so they stay sharp;
        // the rest share the group picture. A tile whose own stream fails goes back to the shared picture for a while.
        val nowForOwn = System.currentTimeMillis()
        val ownIds = if (!liveVideo) {
            emptyList()
        } else {
            buildList {
                for ((i, cam) in ordered.withIndex()) {
                    if (size >= MAX_OWN_STREAMS) break
                    val b = grid.boxes[i]
                    // A tile is "big" if the owner made it Large, Wide or Tall, or if the shared picture would have to be
                    // stretched more than twice to fill it (a camera only gets a small piece of the group picture, so the
                    // Large size looked pixelated): then it gets the camera's own stream.
                    val frac = layout?.fractionFor(cam.optionValue)
                    val stretched = frac != null && request != null && b.w > STRETCH_LIMIT * (frac[2] - frac[0]) * request.width
                    if (shapes[i] == TileShape.Normal && !stretched) continue
                    val onScreen = b.y + b.h > scroll.value && b.y < scroll.value + heightPx
                    if (onScreen && cam.online && nowForOwn >= (ownBlocked[cam.optionValue] ?: 0L)) add(cam.optionValue)
                }
            }
        }
        val ownShown = ownIds.filter { ownShowing[it] == true }

        // Tell the GL view where every live tile is and which part of the picture it shows.
        val tileSpecs = remember(grid, layout, liveNow, ordered, scroll.value, dragId, dragPos, ownShown, motionBoxes) {
            if (!liveNow || layout == null) {
                emptyList()
            } else {
                // The tile being dragged is drawn last, so it sits on top of the others.
                ordered.mapIndexedNotNull { i, cam ->
                    val frac = layout.fractionFor(cam.optionValue) ?: return@mapIndexedNotNull null
                    val box = grid.boxes[i]
                    val dragging = cam.optionValue == dragId
                    val x = if (dragging) dragPos.x else box.x
                    val y = if (dragging) dragPos.y else box.y
                    val portrait = (cam.width ?: 1) < (cam.height ?: 0)
                    // A big tile showing its own stream is not drawn from the shared picture, but still sampled from it.
                    val ownHere = cam.optionValue in ownShown
                    dragging to TileSpec(
                        x.roundToInt(), (y - scroll.value).roundToInt(), box.w.roundToInt(), box.h.roundToInt(), frac, portrait,
                        id = if (motionBoxes) cam.optionValue else "",
                        sampleOnly = ownHere,
                    )
                }.filter { !it.second.sampleOnly || it.second.id.isNotEmpty() }.sortedBy { it.first }.map { it.second }
            }
        }
        // Same frame as the overlays (a LaunchedEffect lags a frame, so the video lagged behind a moved tile).
        SideEffect { glView.setTiles(tileSpecs) }
        // A small grey sample of each live tile every 0.6 s for the motion boxes (the web app's rate).
        LaunchedEffect(motionBoxes, liveNow) {
            while (motionBoxes && liveNow) {
                glView.requestSamples()
                delay(600)
            }
        }

        if (wantLive) {
            AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(with(density) { grid.contentHeight.toDp() })) {
                ordered.forEachIndexed { i, cam -> key(cam.optionValue) { // each tile keeps its own state and gesture when the order changes
                    val box = grid.boxes[i]
                    val inPicture = liveNow && layout?.fractionFor(cam.optionValue) != null
                    val dragging = cam.optionValue == dragId
                    val isOwn = cam.optionValue in ownIds
                    val ownRequest = if (isOwn) {
                        val ratio = minOf(2f, density.density)
                        cameraStreamRequest(
                            box.w / density.density * ratio, box.h / density.density * ratio,
                            cam.width ?: 1920, cam.height ?: 1080,
                        )
                    } else {
                        null
                    }
                    CameraTile(
                        cam = cam,
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .offset {
                                if (dragging) IntOffset(dragPos.x.roundToInt(), dragPos.y.roundToInt())
                                else IntOffset(box.x.roundToInt(), box.y.roundToInt())
                            }
                            .size(with(density) { box.w.toDp() }, with(density) { box.h.toDp() })
                            .semantics {
                                contentDescription = tileDescription(cam, aiLabels, aiTags[cam.optionValue.lowercase()], nowMs)
                                role = Role.Button
                                onClick(label = "Open camera") { onOpenCamera(cam); true }
                                customActions = listOf(CustomAccessibilityAction("Tile size and layout") { sheetFor = cam; true })
                            }
                            .tileGestures(
                                onTap = { onOpenCamera(cam) },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val b = currentGrid.boxes.getOrNull(currentOrdered.indexOfFirst { it.optionValue == cam.optionValue })
                                    if (b != null) dragPos = Offset(b.x, b.y)
                                    dragId = cam.optionValue
                                },
                                onDrag = { delta ->
                                    dragPos += delta
                                    if (swapPending[0]) return@tileGestures
                                    val shown = currentOrdered
                                    val me = shown.indexOfFirst { it.optionValue == cam.optionValue }
                                    val mine = currentGrid.boxes.getOrNull(me)
                                    if (mine != null) {
                                        val cx = dragPos.x + mine.w / 2
                                        val cy = dragPos.y + mine.h / 2
                                        // Swap only when the centre is well inside another tile (its middle 60%), so a tile
                                        // that has just moved aside is not swapped straight back.
                                        val target = currentGrid.boxes.indexOfFirst {
                                            cx >= it.x + it.w * 0.2f && cx < it.x + it.w * 0.8f &&
                                                cy >= it.y + it.h * 0.2f && cy < it.y + it.h * 0.8f
                                        }
                                        if (target >= 0 && target != me) {
                                            swapPending[0] = true
                                            dragArrangement = currentArrangement.moved(shown.map { it.optionValue }, cam.optionValue, target)
                                        }
                                    }
                                },
                                onEnd = { moved ->
                                    dragId = null
                                    dragArrangement?.let { onArrangement(it) }
                                    dragArrangement = null
                                    if (!moved) sheetFor = cam
                                },
                            ),
                        useStill = !inPicture && !(isOwn && ownShowing[cam.optionValue] == true),
                        ownVideo = if (ownRequest != null) {
                            {
                                OwnVideo(
                                    cameraId = cam.optionValue,
                                    request = ownRequest,
                                    portrait = (cam.width ?: 1) < (cam.height ?: 0),
                                    openVideo = openVideo,
                                    onShowing = { on ->
                                        ownShowing[cam.optionValue] = on
                                        if (on) ownFails[cam.optionValue] = 0
                                    },
                                    onFailed = {
                                        val n = (ownFails[cam.optionValue] ?: 0) + 1
                                        ownFails[cam.optionValue] = n
                                        ownBlocked[cam.optionValue] = System.currentTimeMillis() + minOf(300_000L, 30_000L * n)
                                    },
                                )
                            }
                        } else {
                            null
                        },
                        loadStill = loadStill,
                        dimmed = dragging,
                        highlight = highlight,
                        aiLabels = aiLabels,
                        aiTag = aiTags[cam.optionValue.lowercase()],
                        nowMs = nowMs,
                        detector = if (motionBoxes) detectorFor(cam.optionValue) else null,
                        boxTick = if (motionBoxes) boxTick else null,
                        stillRefreshMs = if (motionBoxes) 600L else STILL_REFRESH_MS,
                    )
                } }
            }
        }
        sheetFor?.let { cam ->
            ChoiceDialog(
                title = "${cam.displayName}: tile size",
                options = TileShape.entries,
                selected = arrangement.shapes[cam.optionValue] ?: TileShape.Normal,
                label = { it.label },
                onPick = { onArrangement(arrangement.withShape(cam.optionValue, it)); sheetFor = null },
                onDismiss = { sheetFor = null },
                footer = {
                    TextButton(onClick = { onResetLayout(); sheetFor = null }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Reset layout for this view")
                    }
                    Text(
                        "Press and hold a tile, then drag, to move it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sl.Muted,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                },
            )
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
    ownVideo: (@Composable BoxScope.() -> Unit)? = null,
    dimmed: Boolean = false,
    highlight: Boolean = true,
    aiLabels: Boolean = true,
    aiTag: CameraTag? = null,
    nowMs: Long = 0L,
    /** Finds movement in this tile's pictures and draws boxes (null = motion boxes off). */
    detector: MotionDetector? = null,
    boxTick: MutableLongState? = null,
    stillRefreshMs: Long = STILL_REFRESH_MS,
) {
    val triggered = cam.isTriggered || cam.isAlerting
    val (color, note) = when {
        !cam.online -> Grey to "No signal"
        triggered -> Red to "Triggered"
        cam.isMotion -> Color.White to "Motion"
        else -> Green to ""
    }
    // The web app's rules: an AI result counts while fresh; the border follows its class, else red (trigger) or white (motion).
    val fresh = aiTag != null && aiTag.isFresh(nowMs, triggered)
    val aiClass = if (fresh) aiTag?.tag?.cls else null
    val borderColor: Color? = when {
        !highlight || !cam.online -> null
        triggered -> aiClass?.takeIf { it != AlertClass.Motion }?.let { classColor(it) } ?: Sl.Live
        cam.isMotion -> Color.White.copy(alpha = 0.55f)
        else -> null
    }
    var picture by remember(cam.optionValue) { mutableStateOf<ImageBitmap?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentDetector by rememberUpdatedState(detector)
    LaunchedEffect(cam.optionValue, useStill, stillRefreshMs) {
        if (!useStill) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val bytes = loadStill(cam.optionValue, STILL_WIDTH)
                if (bytes != null) {
                    // Decode, and (with motion boxes on) take the small grey sample for the detector, off the main thread.
                    val decoded = withContext(Dispatchers.Default) {
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                        val sample = if (currentDetector != null) graySampleOf(bmp) else null
                        Triple(bmp.asImageBitmap(), sample, bmp.width to bmp.height)
                    }
                    if (decoded != null) {
                        picture = decoded.first
                        val det = currentDetector
                        if (det != null && decoded.second != null) {
                            det.update(decoded.second!!, "still", SystemClock.uptimeMillis(), decoded.third.first, decoded.third.second)
                            boxTick?.longValue = SystemClock.uptimeMillis()
                        }
                    }
                }
                delay(stillRefreshMs)
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (useStill) Modifier.background(Color.Black) else Modifier)
            .then(if (dimmed) Modifier.border(2.dp, Sl.Accent, RoundedCornerShape(6.dp)) else Modifier),
    ) {
        if (useStill) {
            picture?.let {
                Image(it, contentDescription = cam.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        ownVideo?.invoke(this)
        if (detector != null && boxTick != null) {
            val portrait = (cam.width ?: 1) < (cam.height ?: 0)
            Canvas(Modifier.fillMaxSize()) {
                boxTick.longValue // read here so only this drawing is redone when boxes change or fade
                val now = SystemClock.uptimeMillis()
                val boxes = detector.visible(now)
                val pw = detector.pictureWidth
                val ph = detector.pictureHeight
                if (boxes.isEmpty() || pw <= 0 || ph <= 0) return@Canvas
                // Tiles crop to fill (portrait cameras are shown whole): fit the picture the boxes were found in.
                val scale = if (portrait) minOf(size.width / pw, size.height / ph) else maxOf(size.width / pw, size.height / ph)
                val rw = pw * scale
                val rh = ph * scale
                val rx = (size.width - rw) / 2f
                val ry = (size.height - rh) / 2f
                val pad = 3.dp.toPx()
                for (b in boxes) {
                    val age = (now - b.t).toFloat() / MotionDetector.LINGER_MS
                    val alpha = if (age < 0.5f) 1f else (1f - (age - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    drawRect(
                        Sl.Accent.copy(alpha = alpha),
                        topLeft = Offset(rx + b.x * rw - pad, ry + b.y * rh - pad),
                        size = androidx.compose.ui.geometry.Size(b.w * rw + pad * 2, b.h * rh + pad * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }
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
        if (fresh && aiLabels && aiTag != null) {
            Text(
                aiTag.tag.text,
                color = classColor(aiTag.tag.cls),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 7.dp, top = 34.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(7.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (borderColor != null) {
            val shape = RoundedCornerShape(6.dp)
            Box(Modifier.fillMaxSize().border(3.dp, borderColor, shape)) {
                if (triggered) Box(Modifier.fillMaxSize().padding(3.dp).border(9.dp, borderColor.copy(alpha = 0.2f), shape))
            }
        }
    }
}

/** A small greyscale copy of [bmp], 128 pixels wide, for the motion detector. */
internal fun graySampleOf(bmp: Bitmap): GraySample {
    val w = MotionDetector.SAMPLE_WIDTH
    val h = maxOf(8, (w * bmp.height / bmp.width.toFloat()).roundToInt())
    val small = Bitmap.createScaledBitmap(bmp, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    if (small !== bmp) small.recycle()
    val g = ByteArray(w * h)
    for (i in g.indices) {
        val c = px[i]
        g[i] = ((((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8).toByte()
    }
    return GraySample(g, w, h)
}

/** The colour the app uses for each kind of alert (the web app's tokens). */
fun classColor(cls: AlertClass): Color = when (cls) {
    AlertClass.Person -> Sl.Person
    AlertClass.Vehicle -> Sl.Vehicle
    AlertClass.Animal -> Sl.Animal
    AlertClass.Delivery -> Sl.Delivery
    AlertClass.Motion -> Sl.Motion
}

/** What a screen reader says for a camera tile: its name and, if any, what is happening on it. */
private fun tileDescription(cam: Camera, aiLabels: Boolean, aiTag: CameraTag?, nowMs: Long): String {
    val triggered = cam.isTriggered || cam.isAlerting
    val state = when {
        !cam.online -> "no signal"
        triggered -> "triggered"
        cam.isMotion -> "motion"
        else -> ""
    }
    val ai = if (aiLabels && aiTag != null && aiTag.isFresh(nowMs, triggered)) aiTag.tag.text else ""
    return listOf(cam.displayName, state, ai).filter { it.isNotEmpty() }.joinToString(", ")
}
