package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.content.res.Configuration
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.isSpecified
import android.graphics.SurfaceTexture
import androidx.compose.ui.layout.onSizeChanged
import com.feerlessleadr.sightline.video.CalendarMath
import com.feerlessleadr.sightline.video.ZoomMath
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import kotlin.math.roundToInt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import com.feerlessleadr.sightline.video.GroupLayout
import com.feerlessleadr.sightline.data.Exports
import com.feerlessleadr.sightline.data.ExportOptions
import androidx.compose.material3.LinearProgressIndicator
import com.feerlessleadr.sightline.data.AlertFlags
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.ui.unit.IntSize
import com.feerlessleadr.sightline.video.GridCell
import com.feerlessleadr.sightline.video.GroupGrid
import com.feerlessleadr.sightline.video.TileSpec
import com.feerlessleadr.sightline.video.GroupGlView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.feerlessleadr.sightline.video.MotionDetector
import com.feerlessleadr.sightline.video.MuLawPlayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.feerlessleadr.sightline.data.AlertClips
import com.feerlessleadr.sightline.data.AlertItem
import com.feerlessleadr.sightline.data.SessionExpiredException
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.Timeline
import com.feerlessleadr.sightline.data.VideoQuality
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.StreamEndedException
import com.feerlessleadr.sightline.video.StreamRequest
import com.feerlessleadr.sightline.video.TimeWindow
import com.feerlessleadr.sightline.video.TimelineMath
import com.feerlessleadr.sightline.video.playLive
import com.feerlessleadr.sightline.video.playerStreamRequest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the player asks Blue Iris for. */
class PlayerApi(
    val openLive: suspend (camera: String, size: StreamRequest?, audio: Boolean, group: Boolean) -> VideoStream,
    val openRecorded: suspend (camera: String, positionMs: Long, speedPercent: Int, size: StreamRequest?, audio: Boolean, group: Boolean) -> VideoStream,
    val openClip: suspend (file: String, offsetMs: Long, speedPercent: Int, size: StreamRequest?, audio: Boolean) -> VideoStream,
    val liveStill: suspend (camera: String, width: Int) -> ByteArray?,
    val recordedStill: suspend (camera: String, positionMs: Long, width: Int, group: Boolean) -> ByteArray,
    val timeline: suspend (camera: String, startMs: Long, endMs: Long, msecPerStep: Long) -> Timeline,
    val alerts: suspend (camera: String, startMs: Long, endMs: Long) -> List<AlertItem>,
    val setFlags: suspend (path: String, flags: Long) -> Unit,
)

private const val DAY_MS = 86_400_000L

/** The recording file of an alert being watched: when it starts and ends, whether it already ended or could not be opened. */
private data class AlertClip(val file: String, val start: Long, val end: Long, val endedAt: Long = 0L, val broken: Boolean = false)

/**
 * One camera, live or from its recordings. The picture comes from a stream that restarts whenever the position,
 * speed or mode changes; while a recording is paused or being scrubbed a still picture of that moment is shown
 * instead. The timeline shows what is recorded and where the alerts are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    camera: Camera,
    quality: VideoQuality,
    api: PlayerApi,
    cameras: List<Camera>,
    onSwitchCamera: (Camera) -> Unit,
    /** The other side of the group button: the group to open from a camera, or the camera to return to from a group. */
    groupTarget: Camera?,
    findCamera: (String) -> Camera?,
    motionBoxes: Boolean,
    sensitivity: Int,
    onMotionBoxes: (Boolean) -> Unit,
    soundOn: Boolean,
    volume: Float,
    onSound: (on: Boolean, volume: Float) -> Unit,
    exportOptions: ExportOptions,
    onExportOptions: (ExportOptions) -> Unit,
    download: DownloadState?,
    onDownloadAlert: (AlertItem, String) -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownload: () -> Unit,
    onQuality: (VideoQuality) -> Unit,
    onBack: () -> Unit,
) {
    val id = camera.optionValue
    var surface by remember { mutableStateOf<Surface?>(null) }
    var status by remember { mutableStateOf("Connecting…") }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var aspect by remember(id) { mutableStateOf((camera.width ?: 16).toFloat() / (camera.height ?: 9).toFloat()) }
    var still by remember { mutableStateOf<ImageBitmap?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var pickQuality by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }

    // What is being watched: live, or a recording that is playing or paused at [pos].
    var live by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableIntStateOf(1) }
    var serial by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var resumeAfterScrub by remember { mutableStateOf(false) }
    var window by remember { mutableStateOf(TimelineMath.liveWindow(900_000L, System.currentTimeMillis())) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var timeline by remember(id) { mutableStateOf(Timeline.Empty) }
    var timelineNote by remember(id) { mutableStateOf<String?>(null) }
    var alerts by remember(id) { mutableStateOf<List<AlertItem>>(emptyList()) }
    val frameUtc = remember { AtomicLong(0L) }

    // Sound: the stream is asked to include audio once sound has been on (and stays so until the player closes, so
    // muting is instant); playback volume goes through the player, 0 = muted.
    val sound = remember { MuLawPlayer() }
    var askAudio by remember { mutableStateOf(soundOn && volume > 0f) }
    var streamHasAudio by remember(id) { mutableStateOf<Boolean?>(null) }
    var showVolume by remember { mutableStateOf(false) }
    LaunchedEffect(soundOn, volume) { if (soundOn && volume > 0f) askAudio = true }
    SideEffect { sound.gain = if (soundOn) volume else 0f }
    DisposableEffect(sound) { onDispose { sound.stop() } }
    var clip by remember(id) { mutableStateOf<AlertClip?>(null) }
    // What the calendar learned about each month for this camera, kept while the player is open.
    val monthCache = remember(id) { HashMap<java.time.YearMonth, com.feerlessleadr.sightline.video.MonthInfo>() }
    // Flag or protect the alert being watched (Blue Iris keeps both in the alert's flags); the change shows at once and is
    // undone if Blue Iris refuses.
    val markScope = rememberCoroutineScope()
    var alertItem by remember(id) { mutableStateOf<AlertItem?>(null) }
    var flagOverrides by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var showMark by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    var markBusy by remember { mutableStateOf(false) }
    var markMessage by remember { mutableStateOf<String?>(null) }
    fun flagsOf(a: AlertItem): Long = flagOverrides[AlertFlags.recordId(a.path)] ?: a.flags
    fun toggleMark(bit: Int) {
        val a = alertItem ?: return
        if (markBusy) return
        val key = AlertFlags.recordId(a.path)
        val before = flagsOf(a)
        val want = !AlertFlags.has(before, bit)
        val after = AlertFlags.with(before, bit, want)
        markBusy = true
        flagOverrides = flagOverrides + (key to after)
        markScope.launch {
            try {
                api.setFlags(a.path, after)
                markMessage = if (bit == AlertFlags.FLAGGED) {
                    if (want) "Flagged." else "Flag removed."
                } else {
                    if (want) "Protected: Blue Iris will keep this alert." else "No longer protected."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BlueIrisException) {
                flagOverrides = flagOverrides + (key to before)
                markMessage = AlertFlags.friendlyError(e.message)
            } finally {
                markBusy = false
            }
        }
    }

    // Digital zoom (pinch the picture, drag to pan, double tap to reset). In Auto quality a zoomed picture asks Blue
    // Iris for a sharper stream once the zoom has settled, like the web app.
    var zoom by remember(id) { mutableFloatStateOf(1f) }
    var panX by remember(id) { mutableFloatStateOf(0f) }
    var panY by remember(id) { mutableFloatStateOf(0f) }
    var boost by remember(id) { mutableFloatStateOf(1f) }
    var fill by remember(id) { mutableStateOf(false) } // double tap: fit the whole picture, or fill the screen
    var fullScreen by remember { mutableStateOf(false) }
    // Group view: where each camera sits in the combined picture (from the stream's headers), for tapping one.
    var groupLayout by remember(id) { mutableStateOf<GroupLayout?>(null) }
    // Group view without the cameras the app hides: Blue Iris's picture holds every camera of the group, so when some are
    // hidden or disabled the shown ones are cut out of it and drawn in a grid of their own (the web app's way).
    val context = LocalContext.current
    val groupShown = if (camera.isGroup) camera.group.orEmpty().filter { findCamera(it) != null } else emptyList()
    val useGrid = camera.isGroup && groupShown.isNotEmpty() && groupShown.size < camera.group.orEmpty().size
    val glView = remember(useGrid) { if (useGrid) GroupGlView(context) else null }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(glView, lifecycleOwner) {
        if (glView == null) return@DisposableEffect onDispose { }
        glView.onSurface = { surface = it }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> glView.onResume()
                Lifecycle.Event.ON_STOP -> {
                    surface = null // stops the stream; a new surface comes with the next start
                    glView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) glView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onSurface = null
            glView.onPause()
            glView.release()
            surface = null
        }
    }
    var stageW by remember { mutableFloatStateOf(0f) }
    var stageH by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(id) {
        snapshotFlow { zoom }.collectLatest { z ->
            delay(900)
            boost = if (z >= 2.2f) 2.5f else if (z >= 1.4f) 1.6f else 1f
        }
    }

    val metrics = LocalResources.current.displayMetrics
    // The stream asked for: the size the screen needs, times the zoom boost (Auto quality only).
    val streamSize = remember(quality, boost, id, metrics.widthPixels) {
        val b = if (quality == VideoQuality.Auto) boost else 1f
        playerStreamRequest(
            metrics.widthPixels / metrics.density * b, metrics.heightPixels / metrics.density * b, metrics.density,
            camera.width ?: 1920, camera.height ?: 1080, quality,
        )
    }

    fun now() = System.currentTimeMillis()

    /** The alert recording file to play from at [position], or null (use the timeline). */
    fun clipFor(position: Long): AlertClip? {
        val c = clip ?: return null
        if (c.broken || !AlertClips.isPlayableFile(c.file) || position < c.start || position >= c.end) return null
        if (c.endedAt > 0 && position >= c.endedAt - 1000) return null
        return c
    }

    fun goLive() {
        live = true
        playing = true
        scrubbing = false
        note = null
        error = null
        frameUtc.set(0L)
        window = TimelineMath.liveWindow(window.length, now())
        serial++
    }

    fun enterRecording(position: Long, play: Boolean) {
        val p = min(position, TimelineMath.latestPlayable(now()))
        live = false
        playing = play
        scrubbing = false
        pos = p
        note = null
        error = null
        frameUtc.set(0L)
        window = TimelineMath.centered(p, window.length, now())
        serial++
    }

    /** Moves to [position] keeping the playing or paused state (live moves into playing a recording). */
    fun seek(position: Long) {
        if (position > TimelineMath.latestPlayable(now())) {
            goLive()
            return
        }
        if (live) {
            enterRecording(position, true)
            return
        }
        pos = position
        note = null
        frameUtc.set(0L)
        if (position < window.start || position > window.end) window = TimelineMath.centered(position, window.length, now())
        serial++
    }

    fun setZoom(length: Long) {
        window = if (live) TimelineMath.liveWindow(length, now()) else TimelineMath.centered(pos, length, now())
    }

    fun jumpAlert(direction: Int) {
        val ref = if (live) now() else pos + 2000
        val target = if (direction < 0) alerts.lastOrNull { it.timeMs < ref - 1500 } else alerts.firstOrNull { it.timeMs > ref + 1500 }
        if (target == null) {
            note = if (direction < 0) "No earlier alert in the last 7 days." else "No later alert."
            return
        }
        if (window.length > 900_000L) window = TimeWindow(window.end - 120_000L, window.end)
        if (camera.isGroup) {
            enterRecording(target.timeMs - 2000, true) // a group: everyone jumps to that moment (no single recording file)
            return
        }
        alertItem = target
        val file = AlertClips.fileFor(target.clip, target.path)
        val alertClip = AlertClip(file, target.timeMs - target.offsetMs, target.timeMs + AlertClips.durationMillis(target.fileSize))
        clip = alertClip
        enterRecording(max(target.timeMs - 2000, alertClip.start), true)
    }

    /** One frame back or forward (the frame length comes from the camera's frame rate): pauses, then shows that moment. */
    fun stepFrame(direction: Int) {
        if (live) {
            if (direction > 0) return // nothing newer than live
            // From live: pause on the frame shown, then step back from there.
            val shown = frameUtc.get()
            enterRecording(if (shown > 0) shown else TimelineMath.latestPlayable(now()), false)
        } else if (playing) {
            val shown = frameUtc.get()
            if (shown > 0) pos = shown
            playing = false
        }
        val target = TimelineMath.stepTarget(pos, direction, TimelineMath.frameIntervalMs(if (camera.isGroup) 30.0 else camera.fps), now()) ?: return
        seek(target)
    }

    fun togglePlay() {
        if (live) {
            val shown = frameUtc.get()
            enterRecording(if (shown > 0) shown else TimelineMath.latestPlayable(now()), false)
        } else {
            playing = !playing
            note = null
            serial++
        }
    }

    // The picture: a live or recorded stream, whenever it is wanted and the video surface exists.
    // Counts the streams started, so that a stream that has been replaced (another camera, a seek) can no longer change
    // what is on screen while it is still shutting down: its late reports used to uncover the previous camera.
    val streamGeneration = remember { IntArray(1) }
    LaunchedEffect(surface, live, playing, scrubbing, serial, streamSize, attempt, id, askAudio) {
        val mine = ++streamGeneration[0]
        val target = surface
        if (target == null || scrubbing || !(live || playing)) {
            if (streaming) still = null // the surface keeps the last frame; do not cover it with an older still
            streaming = false
            return@LaunchedEffect
        }
        status = "Connecting…"
        error = null
        stats = ""
        streaming = false
        val startAt = pos
        val atSpeed = speed
        val isLive = live
        var fromFile = false
        try {
            val size = streamSize
            val started = SystemClock.elapsedRealtime()
            // Inside an alert, play from the alert's own recording file (as the web app does: on some cameras the
            // timeline shows an alert cropped to a corner); anywhere else, or if the file cannot be had, the timeline.
            val fileClip = if (isLive) null else clipFor(startAt)
            val stream = when {
                isLive -> api.openLive(id, size, askAudio && !camera.isGroup, camera.isGroup)
                fileClip != null -> try {
                    api.openClip(fileClip.file, startAt - fileClip.start, atSpeed * 100, size, askAudio && atSpeed == 1).also { fromFile = true }
                } catch (e: SessionExpiredException) {
                    throw e
                } catch (e: BlueIrisException) {
                    clip = fileClip.copy(broken = true) // do not try this file again
                    api.openRecorded(id, startAt, atSpeed * 100, size, askAudio && atSpeed == 1 && !camera.isGroup, camera.isGroup)
                }
                else -> api.openRecorded(id, startAt, atSpeed * 100, size, askAudio && atSpeed == 1 && !camera.isGroup, camera.isGroup)
            }
            val connected = SystemClock.elapsedRealtime() - started
            status = "Waiting for the first picture…"
            var firstPicture = -1L
            playLive(
                stream, target,
                onSize = { w, h ->
                    if (streamGeneration[0] == mine) {
                        aspect = w.toFloat() / h.toFloat()
                        glView?.setVideoSize(w, h)
                        if (camera.isGroup) groupLayout = groupLayoutFor(camera, stream, w, h, isLive)
                    }
                },
                onStats = { s ->
                    if (streamGeneration[0] != mine) return@playLive
                    if (s.shown > 0 && firstPicture < 0) firstPicture = SystemClock.elapsedRealtime() - started
                    stats = "${if (isLive) "live" else if (fromFile) "alert file x$atSpeed" else "recorded x$atSpeed"}: $s; asked for ${size.width}x${size.height} ${size.kbps} kbps; " +
                        "connection opened after $connected ms, first picture $firstPicture ms after the request started"
                    if (s.shown > 0) {
                        status = ""
                        streaming = true
                        still = null
                    }
                },
                onFrameTime = { if (streamGeneration[0] == mine) frameUtc.set(it) },
                speed = if (isLive) null else atSpeed.toFloat(),
                clipBaseMs = if (fromFile) startAt else null,
                audio = sound,
                onHasAudio = { if (streamGeneration[0] == mine) streamHasAudio = it },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: StreamEndedException) {
            if (streamGeneration[0] != mine) return@LaunchedEffect
            val ended = clip
            if (isLive) {
                error = e.message
            } else if (fromFile && ended != null) {
                // The end of the alert's own file: carry on along the timeline from there.
                val last = max(frameUtc.get(), startAt)
                clip = ended.copy(endedAt = last)
                pos = last
                serial++
            } else {
                // The end of what Blue Iris could play: if that is (nearly) now, carry on live.
                val last = max(frameUtc.get(), pos)
                if (last >= TimelineMath.latestPlayable(now()) - 20_000L) {
                    goLive()
                } else {
                    pos = last
                    playing = false
                    note = "That is the end of the recording here."
                }
            }
        } catch (e: BlueIrisException) {
            if (streamGeneration[0] != mine) return@LaunchedEffect
            error = e.message
        }
    }

    // While playing a recording the position follows the frames; live keeps the window ending at "now".
    LaunchedEffect(Unit) {
        var lastLiveWindow = 0L
        while (true) {
            delay(200)
            val n = now()
            nowMs = n
            if (live) {
                if (n - lastLiveWindow > 5_000L) {
                    lastLiveWindow = n
                    window = TimelineMath.liveWindow(window.length, n)
                }
            } else if (playing && !scrubbing && streaming) {
                val t = frameUtc.get()
                if (t > 0) {
                    pos = t
                    if (t > TimelineMath.latestPlayable(n)) {
                        goLive()
                    } else if (TimelineMath.shouldFollow(t, window, n)) {
                        window = TimelineMath.centered(t + (window.length * 0.3).toLong(), window.length, n)
                    }
                }
            }
        }
    }

    // A still picture: a recording that is paused or scrubbed shows its moment; the first moments of a stream show the latest.
    val stillKey = if (!live && (!playing || scrubbing)) pos else 0L
    val stillWidth = if (boost > 1f && quality == VideoQuality.Auto) 1920 else 960
    LaunchedEffect(live, playing, scrubbing, stillKey, serial, id, stillWidth) {
        if (streaming) return@LaunchedEffect
        if (live) {
            val bytes = api.liveStill(id, 960) ?: return@LaunchedEffect
            val decoded = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            if (decoded != null && !streaming) still = decoded
        } else {
            if (scrubbing || !playing) delay(150) // wait for the finger to settle
            try {
                val bytes = api.recordedStill(id, pos, stillWidth, camera.isGroup)
                val decoded = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                if (decoded != null && !streaming) {
                    still = decoded
                    note = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BlueIrisException) {
                if (!playing || scrubbing) note = "No picture for that time. ${e.message}"
            }
        }
    }

    // What is recorded and where the alerts are, for the part of time the bar shows.
    LaunchedEffect(window.start / 1000, window.end / 1000, id) {
        delay(250)
        try {
            timeline = api.timeline(id, window.start, window.end, TimelineMath.msecPerStep(window.length))
            timelineNote = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlueIrisException) {
            timelineNote = e.message
        }
    }
    LaunchedEffect(id) {
        try {
            alerts = api.alerts(id, now() - 7 * DAY_MS, now() + 60_000L)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlueIrisException) {
            // Without the list the previous/next alert buttons just do nothing.
        }
    }

    // Keep the screen awake while the player is open, like the web app.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Full screen hides the system bars. In full screen, and on a landscape phone, the controls float over the picture
    // (a tap shows or hides them; in full screen they also fade after 3 s of watching), as in the web app.
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && configuration.screenHeightDp <= 540
    val overlay = fullScreen || landscape
    SystemBarsHidden(fullScreen)
    BackHandler(enabled = fullScreen) { fullScreen = false }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val busy = showVolume || pickQuality || showGoTo || scrubbing || (!live && !playing)
    LaunchedEffect(fullScreen) { controlsVisible = true }
    LaunchedEffect(fullScreen, controlsVisible, lastTouch, busy) {
        if (fullScreen && controlsVisible && !busy) {
            delay(3_000)
            controlsVisible = false
        }
    }
    // One tap on the picture: floating controls show or hide; otherwise a live camera closes back to where it was
    // opened and a recording pauses or resumes (like UI3 and the web app).
    // The cells of the grid that replaces Blue Iris's group picture (only when the app hides some of the group's cameras).
    val gridLayout = groupLayout ?: (if (live) listLayoutOf(camera) else null)
    val gridCells: List<GridCell> = remember(useGrid, gridLayout, groupShown, stageW, stageH, aspect) {
        when {
            !useGrid || stageW <= 0f || stageH <= 0f -> emptyList()
            gridLayout != null -> GroupGrid.cells(gridLayout, groupShown, stageW, stageH)
            else -> {
                // No layout known (a recording): Blue Iris's whole picture as it is, fitted in the stage.
                val fit = ZoomMath.fit(stageW, stageH, aspect, false)
                listOf(GridCell("", fit[0], fit[1], fit[2], fit[3], floatArrayOf(0f, 0f, 1f, 1f)))
            }
        }
    }
    // A tap in group view: show the camera that was tapped, at the same moment (live stays live, a recording keeps its time).
    fun jumpToCameraAt(at: Offset) {
        if (useGrid) {
            val qx = (at.x - panX) / zoom
            val qy = (at.y - panY) / zoom
            val name = gridCells.firstOrNull { it.camera.isNotEmpty() && it.contains(qx, qy) }?.camera ?: return
            findCamera(name)?.let { onSwitchCamera(it) }
            return
        }
        val layout = groupLayout ?: return
        val fit = ZoomMath.fit(stageW, stageH, aspect, fill)
        if (zoom <= 0f || fit[2] <= 0f || fit[3] <= 0f) return
        val fx = ((at.x - panX) / zoom - fit[0]) / fit[2]
        val fy = ((at.y - panY) / zoom - fit[1]) / fit[3]
        val name = layout.cameraAt(fx, fy) ?: return
        findCamera(name)?.let { onSwitchCamera(it) }
    }
    val onStageTap by rememberUpdatedState<(Offset) -> Unit> { at ->
        lastTouch = SystemClock.elapsedRealtime()
        when {
            overlay -> controlsVisible = !controlsVisible
            camera.isGroup -> jumpToCameraAt(at)
            live -> onBack()
            else -> togglePlay()
        }
    }
    // Swiping to another camera: the picture follows the finger and the next camera's picture slides in beside it. The new
    // camera's picture is a still frame (its video starts only once it is chosen); the video takes over from it.
    val density = LocalDensity.current
    val swipeMinPx = with(density) { 60.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var swipeBusy by remember { mutableStateOf(false) }
    var neighbourStills by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    // A snapshot of the picture that is leaving, slid instead of the live video view (a live video view jerks when moved).
    var outgoingStill by remember { mutableStateOf<ImageBitmap?>(null) }
    val textureHolder = remember { arrayOfNulls<TextureView>(1) }
    val captureOutgoing by rememberUpdatedState<() -> Unit> {
        val tv = textureHolder[0]
        // Only when the video view is what is on screen (not when a still already covers it).
        if (tv != null && tv.isAvailable && stageW > 0f && stageH > 0f && (streaming || still == null)) {
            // The snapshot is the raw video stretched to the size asked for (the view's fit transform is not applied), so ask
            // for the picture's own proportions and draw it in the picture's place.
            val fit = ZoomMath.fit(stageW, stageH, aspect, false)
            val bitmap = runCatching { tv.getBitmap((fit[2] / 2).toInt().coerceAtLeast(1), (fit[3] / 2).toInt().coerceAtLeast(1)) }.getOrNull()
            outgoingStill = bitmap?.asImageBitmap()
        }
    }
    val cameraIndex = cameras.indexOfFirst { it.optionValue == id }.coerceAtLeast(0)
    fun neighbour(direction: Int): Camera? =
        if (cameras.size < 2) null else cameras[(cameraIndex + direction + cameras.size) % cameras.size]

    suspend fun loadNeighbour(other: Camera) {
        val bytes = try {
            if (live) api.liveStill(other.optionValue, 640) else api.recordedStill(other.optionValue, pos, 640, other.isGroup)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BlueIrisException) {
            null
        } ?: return
        val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return
        val keep = setOfNotNull(neighbour(1)?.optionValue, neighbour(-1)?.optionValue, id)
        neighbourStills = (neighbourStills + (other.optionValue to bitmap)).filterKeys { it in keep }
    }
    // Live stills of both neighbours are fetched a moment after a camera opens so a swipe has something to show at once.
    LaunchedEffect(id, live, cameras.size) {
        if (!live) return@LaunchedEffect
        delay(1_200)
        listOfNotNull(neighbour(1), neighbour(-1)).forEach { loadNeighbour(it) }
    }
    val onSwipeStart by rememberUpdatedState<suspend () -> Unit> {
        coroutineScope { listOfNotNull(neighbour(1), neighbour(-1)).forEach { launch { loadNeighbour(it) } } }
    }
    // Let go of a swipe: a long or quick one slides the rest of the way and switches camera (keeping the moment: live
    // stays live, a recording keeps its time); a short one springs back.
    val finishSwipe by rememberUpdatedState<suspend (Float, Boolean) -> Unit> { dx, quick ->
        val w = stageW
        val target = neighbour(if (dx < 0) 1 else -1)
        val complete = target != null && w > 0f && (abs(dx) > w * 0.4f || (quick && abs(dx) > swipeMinPx))
        swipeBusy = true
        if (!complete || target == null) {
            animate(swipeOffset, 0f, animationSpec = tween(160)) { v, _ -> swipeOffset = v }
            outgoingStill = null // the live video is still running underneath and is shown again
        } else {
            animate(swipeOffset, if (dx < 0) -w else w, animationSpec = tween(180)) { v, _ -> swipeOffset = v }
            // The new camera's picture is fully in place: switch to it, keeping that picture up until its video starts.
            still = neighbourStills[target.optionValue] ?: ImageBitmap(1, 1)
            streaming = false
            outgoingStill = null
            swipeOffset = 0f
            lastTouch = SystemClock.elapsedRealtime()
            onSwitchCamera(target)
        }
        swipeBusy = false
    }
    // Motion boxes: the picture on screen (video or still) is sampled small about every 150 ms and compared with the
    // last one, like the web app; a new stream starts the comparison over.
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val detector = remember(id) { MotionDetector { currentSensitivity } }
    val boxTick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(motionBoxes, id, live, serial) {
        if (!motionBoxes) {
            detector.reset()
            return@LaunchedEffect
        }
        var lastFrame = 0L
        var lastStill: ImageBitmap? = null
        while (true) {
            delay(150)
            val now = SystemClock.uptimeMillis()
            val tv = textureHolder[0]
            if (swipeOffset == 0f) {
                if (streaming && tv != null && tv.isAvailable) {
                    val frame = frameUtc.get()
                    if (frame != lastFrame) {
                        lastFrame = frame
                        val bitmap = runCatching {
                            tv.getBitmap(MotionDetector.SAMPLE_WIDTH, (MotionDetector.SAMPLE_WIDTH / aspect).roundToInt().coerceAtLeast(8))
                        }.getOrNull()
                        if (bitmap != null) {
                            val sample = withContext(Dispatchers.Default) { graySampleOf(bitmap) }
                            detector.update(sample, (if (live) "live-" else "rec-") + id + "-" + serial, now)
                        }
                    }
                } else if (!streaming) {
                    val picture = still
                    if (picture != null && picture !== lastStill && picture.width > 1) {
                        lastStill = picture
                        val sample = withContext(Dispatchers.Default) { graySampleOf(picture.asAndroidBitmap()) }
                        detector.update(sample, "still-" + id, now)
                    }
                }
            }
            boxTick.longValue = now
        }
    }
    val onStageDoubleTap by rememberUpdatedState<() -> Unit> {
        lastTouch = SystemClock.elapsedRealtime()
        if (zoom > 1f) {
            zoom = 1f; panX = 0f; panY = 0f
        } else {
            fill = !fill // fit the whole picture, or fill the screen and crop the edges
            zoom = 1f; panX = 0f; panY = 0f
        }
    }

    // Parts that appear in more than one layout.
    val title = if (camera.isGroup) "${camera.displayName} · ${if (useGrid) groupShown.size else (camera.group?.size ?: 0)}" else camera.displayName
    val soundButton: @Composable () -> Unit = {
        Box {
            IconAction(if (soundOn && volume > 0f) Glyph.Sound else Glyph.Muted, "Volume") { showVolume = true }
            DropdownMenu(expanded = showVolume, onDismissRequest = { showVolume = false }) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(if (soundOn) Glyph.Sound else Glyph.Muted, if (soundOn) "Mute" else "Unmute") {
                        val on = !soundOn
                        onSound(on, if (on && volume == 0f) 1f else volume)
                    }
                    Slider(
                        value = if (soundOn) volume else 0f,
                        onValueChange = { onSound(it > 0f, it) },
                        valueRange = 0f..2f,
                        modifier = Modifier.width(160.dp),
                    )
                    Text(if (soundOn) "${(volume * 100).roundToInt()}%" else "Muted", modifier = Modifier.padding(start = 8.dp), fontSize = 13.sp)
                }
                if (askAudio && streamHasAudio == false) {
                    Text("This camera is not sending sound.", color = Sl.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
    val fullScreenButton: @Composable () -> Unit = {
        IconAction(if (fullScreen) Glyph.ExitFullScreen else Glyph.FullScreen, if (fullScreen) "Exit full screen" else "Full screen") {
            fullScreen = !fullScreen
        }
    }
    val groupButton: @Composable () -> Unit = {
        val target = groupTarget
        if (target != null) {
            IconAction(
                Glyph.Group,
                if (camera.isGroup) "Back to ${target.displayName}" else "Watch all cameras at the same moment",
                tint = if (camera.isGroup) Sl.Accent else Sl.Text,
            ) { onSwitchCamera(target) }
        }
    }
    val boxesButton: @Composable () -> Unit = {
        IconAction(Glyph.Boxes, if (motionBoxes) "Motion boxes on" else "Motion boxes off", tint = if (motionBoxes) Sl.Accent else Sl.Text) {
            onMotionBoxes(!motionBoxes)
        }
    }
    val qualityButton: @Composable () -> Unit = {
        TextButton(onClick = { pickQuality = true }) { Text(quality.label.uppercase()) }
    }

    // The stage takes all the room it is given. The picture sits in its middle; zooming grows into the whole stage.
    val stage: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .background(if (useGrid) Color.Transparent else Color.Black) // the grid's video view sits behind the window
                .clipToBounds()
                .onSizeChanged { stageW = it.width.toFloat(); stageH = it.height.toFloat() }
                .pointerInput(Unit) {
                    // One handler for everything on the picture, so a drag can never be mistaken for a tap.
                    // A press that stays inside the touch slop and ends soon is a tap (it waits a moment to see whether
                    // a second one makes it a double tap). Two fingers zoom around the point between them; one finger
                    // pans when the picture is zoomed in or filling the screen.
                    val slop = viewConfiguration.touchSlop
                    var lastTapAt = 0L
                    var pendingTap: Job? = null
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            lastTouch = SystemClock.elapsedRealtime()
                            var moved = false
                            var multi = false
                            var endedAt = down.uptimeMillis
                            var lastPos = down.position
                            // Sideways swiping to another camera needs a picture that is neither zoomed nor filled.
                            val canSwipe = zoom <= 1f && !fill && cameras.size >= 2 && !swipeBusy
                            var swipeDecided = false
                            var swipeLocked = false
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                endedAt = event.changes.maxOf { it.uptimeMillis }
                                if (fingers >= 2) multi = true
                                val mine = event.changes.firstOrNull { it.id == down.id }
                                if (mine != null && (mine.position - down.position).getDistance() > slop) moved = true
                                if (mine != null) lastPos = mine.position
                                if (canSwipe && !multi && fingers == 1 && mine != null && moved) {
                                    val sx = mine.position.x - down.position.x
                                    val sy = mine.position.y - down.position.y
                                    if (!swipeDecided) {
                                        // The first real movement decides: clearly sideways follows the finger, anything else does not.
                                        swipeDecided = true
                                        swipeLocked = abs(sx) > abs(sy) * 1.5f
                                        if (swipeLocked) {
                                            captureOutgoing() // before anything moves
                                            launch { onSwipeStart() }
                                        }
                                    }
                                    if (swipeLocked) {
                                        swipeOffset = sx
                                        mine.consume()
                                    }
                                }
                                // The last event of a gesture has no finger down, so there is no centre to work with (it
                                // comes back as "not a number", which blanked the picture the moment you let go).
                                val centre = if (fingers > 0) event.calculateCentroid() else androidx.compose.ui.geometry.Offset.Unspecified
                                if (fingers > 0 && centre.isSpecified && (fingers >= 2 || moved) && (fingers >= 2 || zoom > 1f || fill)) {
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val scale = if (fingers >= 2) event.calculateZoom() else 1f
                                    val pan = event.calculatePan()
                                    val next = (zoom * scale).coerceIn(1f, ZoomMath.MAX)
                                    if (next <= 1.01f && !fill) {
                                        zoom = 1f; panX = 0f; panY = 0f
                                    } else {
                                        // The point under the fingers stays under them; the picture never leaves the stage.
                                        val k = next / zoom
                                        val fit = if (useGrid) floatArrayOf(0f, 0f, w, h) else ZoomMath.fit(w, h, aspect, fill)
                                        panX = ZoomMath.clampAxis(fit[0], fit[2], w, next, centre.x - (centre.x - panX) * k + pan.x)
                                        panY = ZoomMath.clampAxis(fit[1], fit[3], h, next, centre.y - (centre.y - panY) * k + pan.y)
                                        zoom = next
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (swipeLocked) {
                                val dx = if (multi) 0f else lastPos.x - down.position.x
                                val quick = endedAt - down.uptimeMillis < 600L
                                launch { finishSwipe(dx, quick) }
                            }
                            if (!moved && !multi && endedAt - down.uptimeMillis < 400L) {
                                if (pendingTap != null && endedAt - lastTapAt < 300L) {
                                    pendingTap?.cancel()
                                    pendingTap = null
                                    lastTapAt = 0L
                                    onStageDoubleTap()
                                } else {
                                    lastTapAt = endedAt
                                    val tapAt = down.position
                                    pendingTap = launch {
                                        delay(300)
                                        pendingTap = null
                                        onStageTap(tapAt)
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            // A group with hidden cameras: the shown cameras are cut out of the group picture by the GL view, drawn in a grid
            // that follows the zoom, panning and swiping (the stage is the "picture" then).
            if (useGrid && glView != null) {
                AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
                val specs = gridCells.map { c ->
                    TileSpec(
                        (c.x * zoom + panX + swipeOffset).roundToInt(), (c.y * zoom + panY).roundToInt(),
                        (c.w * zoom).roundToInt(), (c.h * zoom).roundToInt(), c.src,
                    )
                }
                SideEffect { glView.setTiles(specs) }
            } else AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    TextureView(context).also { tv ->
                        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                surface = Surface(st)
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                surface?.release()
                                surface = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                        if (tv.isAvailable) surface = Surface(tv.surfaceTexture!!)
                        textureHolder[0] = tv
                    }
                },
                update = { tv ->
                    val w = stageW
                    val h = stageH
                    if (w > 0f && h > 0f) {
                        val fit = ZoomMath.fit(w, h, aspect, fill)
                        val m = Matrix()
                        m.postScale(fit[2] / w, fit[3] / h, w / 2f, h / 2f) // the texture is stretched over the view: restore its shape
                        m.postScale(zoom, zoom, 0f, 0f)
                        m.postTranslate(panX, panY)
                        tv.setTransform(m)
                    }
                    tv.alpha = if (outgoingStill != null) 0f else 1f // hidden (not stopped) while its snapshot slides
                },
            )
            // Covers the video only while no video frame is showing (a paused recording, or the start of a stream).
            if (!streaming) {
                still?.let { picture ->
                    Box(Modifier.fillMaxSize().background(Color.Black)) // hides the picture that was there before
                    if (useGrid) {
                        // Cut the shown cameras out of the still, as the video is.
                        Canvas(Modifier.fillMaxSize()) {
                            withTransform({
                                translate(panX + swipeOffset, panY)
                                scale(zoom, zoom, pivot = Offset.Zero)
                            }) {
                                val sw = picture.width
                                val sh = picture.height
                                for (c in gridCells) {
                                    drawImage(
                                        picture,
                                        srcOffset = IntOffset((c.src[0] * sw).roundToInt(), (c.src[1] * sh).roundToInt()),
                                        srcSize = IntSize(((c.src[2] - c.src[0]) * sw).roundToInt().coerceAtLeast(1), ((c.src[3] - c.src[1]) * sh).roundToInt().coerceAtLeast(1)),
                                        dstOffset = IntOffset(c.x.roundToInt(), c.y.roundToInt()),
                                        dstSize = IntSize(c.w.roundToInt().coerceAtLeast(1), c.h.roundToInt().coerceAtLeast(1)),
                                    )
                                }
                            }
                        }
                    } else Image(
                        picture, contentDescription = null, contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX + swipeOffset
                            translationY = panY
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                    )
                }
            }
            // The leaving picture while swiping: a snapshot, which slides smoothly.
            outgoingStill?.let {
                val rect = ZoomMath.fit(stageW, stageH, aspect, false)
                Image(
                    it, contentDescription = null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { IntOffset(rect[0].roundToInt(), rect[1].roundToInt()) }
                        .size(with(density) { rect[2].toDp() }, with(density) { rect[3].toDp() })
                        .graphicsLayer { translationX = swipeOffset },
                )
            }
            // While swiping, the next (or previous) camera's picture comes in from the side, close behind the current one.
            if (swipeOffset != 0f) {
                val other = neighbour(if (swipeOffset < 0f) 1 else -1)
                if (other != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = swipeOffset + (if (swipeOffset < 0f) stageW else -stageW) }
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        val picture = neighbourStills[other.optionValue]
                        if (picture != null) {
                            Image(picture, contentDescription = null, contentScale = if (fill) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                        Text(
                            other.displayName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(if (picture != null) Alignment.TopStart else Alignment.Center)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x99000000))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            // Boxes around movement, drawn in the picture's place so they follow zoom and panning.
            if (motionBoxes && !useGrid && swipeOffset == 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    boxTick.longValue // read here so only this drawing is redone as boxes appear and fade
                    val now = SystemClock.uptimeMillis()
                    val boxes = detector.visible(now, 900L)
                    if (boxes.isEmpty()) return@Canvas
                    val fit = ZoomMath.fit(size.width, size.height, aspect, fill)
                    val pad = 4.dp.toPx()
                    withTransform({
                        translate(panX, panY)
                        scale(zoom, zoom, pivot = androidx.compose.ui.geometry.Offset.Zero)
                    }) {
                        for (b in boxes) {
                            val age = (now - b.t) / 900f
                            val alpha = if (age < 0.4f) 1f else (1f - (age - 0.4f) / 0.6f).coerceIn(0f, 1f)
                            drawRect(
                                Sl.Accent.copy(alpha = alpha),
                                topLeft = androidx.compose.ui.geometry.Offset(fit[0] + b.x * fit[2] - pad / zoom, fit[1] + b.y * fit[3] - pad / zoom),
                                size = androidx.compose.ui.geometry.Size(b.w * fit[2] + 2 * pad / zoom, b.h * fit[3] + 2 * pad / zoom),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx() / zoom),
                            )
                        }
                    }
                }
            }
            // Small labels on the picture; they move down while the floating top bar is showing.
            val labelTop = if (overlay && controlsVisible) 52.dp else 0.dp
            // The camera's name and the moment shown (the top bar has no room left for the name).
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = labelTop)
                    .padding(8.dp)
                    .widthIn(max = with(density) { (stageW * 0.62f).toDp() })
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "  ·  " + (if (live) "Live" else formatWhen(pos)),
                    color = if (live) Sl.Live else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            if (zoom > 1f) {
                Text(
                    "${"%.1f".format(zoom)}×  reset",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = labelTop)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x99000000))
                        .clickable(role = Role.Button) { zoom = 1f; panX = 0f; panY = 0f }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            // The alert being watched: tap to flag or protect it.
            alertItem?.takeIf { AlertFlags.recordId(it.path).isNotEmpty() }?.let { a ->
                val f = flagsOf(a)
                val flagged = AlertFlags.has(f, AlertFlags.FLAGGED)
                val protectedAlert = AlertFlags.has(f, AlertFlags.PROTECTED)
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = labelTop)
                        .padding(end = if (zoom > 1f) 112.dp else 0.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x99000000))
                        .clickable(role = Role.Button, onClickLabel = "Flag or protect this alert") {
                            markMessage = null
                            showMark = true
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(if (flagged) Glyph.FlagOn else Glyph.Flag, if (flagged) Sl.Person else Color.White, 18.dp)
                    if (protectedAlert) GlyphIcon(Glyph.Lock, Sl.Accent, 18.dp)
                }
            }
            if (status.isNotEmpty() && error == null && !streaming && (live || playing) && !scrubbing) {
                Text(
                    status,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            // Messages sit over the bottom of the picture so they never push the controls around.
            // A download in progress (or just finished or failed): what it is doing, with Cancel or OK.
            download?.let { d ->
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = labelTop + 48.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE6000000))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.text, color = if (d.failed) MaterialTheme.colorScheme.error else Sl.Text, style = MaterialTheme.typography.bodyMedium)
                    if (!d.finished) {
                        val percent = d.percent
                        if (percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.8f))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.8f))
                        }
                        TextButton(onClick = onCancelDownload) { Text("Cancel") }
                    } else {
                        TextButton(onClick = onDismissDownload) { Text("OK") }
                    }
                }
            }
            val message = error ?: note
            if (message != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (overlay && controlsVisible) 190.dp else 0.dp)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC000000))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = if (error != null) MaterialTheme.colorScheme.error else Sl.Text, style = MaterialTheme.typography.bodyMedium)
                    if (error != null) {
                        if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.labelSmall, color = Sl.Faint)
                        TextButton(onClick = { attempt++ }) { Text("Retry") }
                    }
                }
            }
        }
    }

    // Timeline, the timeline zoom with the calendar, speed and LIVE, then the transport buttons.
    val controls: @Composable ColumnScope.() -> Unit = {
        TimelineBar(
            window = window,
            timeline = timeline,
            alerts = alerts,
            positionMs = if (live) null else pos,
            nowMs = nowMs,
            onScrub = { t ->
                if (!scrubbing) {
                    scrubbing = true
                    resumeAfterScrub = live || playing
                    live = false
                    playing = false
                }
                pos = min(t, TimelineMath.latestPlayable(now()))
                note = null
                frameUtc.set(0L)
            },
            onScrubEnd = {
                scrubbing = false
                if (resumeAfterScrub) {
                    if (pos >= TimelineMath.latestPlayable(now()) - 1_000L) {
                        goLive()
                    } else {
                        playing = true
                        serial++
                    }
                } else {
                    serial++
                }
            },
            onZoom = { factor, anchor -> window = TimelineMath.zoomed(window, factor, anchor, now()) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        timelineNote?.let {
            Text(
                if (it.contains("denied", true) || it.contains("fail", true)) "Rewinding needs a newer Blue Iris, or this account may not rewind. ($it)" else it,
                style = MaterialTheme.typography.bodySmall,
                color = Sl.Muted,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Sl.Surface)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TimelineMath.ZOOMS.forEach { (length, label) ->
                    val on = window.length == length
                    Text(
                        label,
                        color = if (on) Sl.Accent else Sl.Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Sl.Accent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable(role = Role.Button) { setZoom(length) }
                            .padding(horizontal = 7.dp, vertical = 9.dp),
                    )
                }
                IconAction(Glyph.Calendar, "Go to date and time") { showGoTo = true }
            }
            Pill("${speed}×", selected = speed != 1) {
                speed = TimelineMath.SPEEDS[(TimelineMath.SPEEDS.indexOf(speed) + 1) % TimelineMath.SPEEDS.size]
                if (!live && playing) {
                    val shown = frameUtc.get()
                    if (shown > 0) pos = shown
                    serial++
                }
            }
            Pill("LIVE", selected = live, accent = Sl.Live) { if (!live) goLive() }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconAction(Glyph.PrevAlert, "Previous alert") { jumpAlert(-1) }
            Pill("−10 s", compact = true) { seek((if (live) TimelineMath.latestPlayable(now()) else pos) - 10_000L) }
            IconAction(Glyph.FrameBack, "Previous frame") { stepFrame(-1) }
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Sl.Surface2)
                    .clickable(role = Role.Button, onClickLabel = if (live || playing) "Pause" else "Play") { togglePlay() },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(if (live || playing) Glyph.Pause else Glyph.Play, Sl.Text, 28.dp) }
            IconAction(Glyph.FrameForward, "Next frame") { stepFrame(1) }
            Pill("+10 s", compact = true) { if (!live) seek(pos + 10_000L) }
            IconAction(Glyph.NextAlert, "Next alert") { jumpAlert(1) }
        }
    }

    // Any touch on the floating controls keeps them from fading.
    val touchesControls = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            lastTouch = SystemClock.elapsedRealtime()
        }
    }

    if (overlay) {
        Box(Modifier.fillMaxSize().background(if (useGrid) Color.Transparent else Color.Black)) {
            stage(Modifier.fillMaxSize())
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xC0000000), Color.Transparent)))
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .then(touchesControls)
                        .padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    groupButton()
                    boxesButton()
                    soundButton()
                    fullScreenButton()
                    qualityButton()
                }
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)), startY = 0f))
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .then(touchesControls)
                        .padding(top = 24.dp, bottom = 4.dp),
                ) { controls() }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                    actions = {
                        groupButton()
                        boxesButton()
                    soundButton()
                        fullScreenButton()
                        qualityButton()
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                stage(Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.fillMaxWidth().background(Sl.Background).padding(bottom = 4.dp)) { controls() }
            }
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
    if (showMark) {
        alertItem?.let { a ->
            val f = flagsOf(a)
            MarkSheet(
                info = camera.displayName + " · " + formatWhen(a.timeMs),
                flagged = AlertFlags.has(f, AlertFlags.FLAGGED),
                protectedAlert = AlertFlags.has(f, AlertFlags.PROTECTED),
                busy = markBusy,
                message = markMessage,
                onToggleFlag = { toggleMark(AlertFlags.FLAGGED) },
                onToggleProtect = { toggleMark(AlertFlags.PROTECTED) },
                onDownload = if (Exports.canExport(a)) ({ showMark = false; showDownload = true }) else null,
                onDone = { showMark = false },
            )
        }
    }
    if (showDownload) {
        alertItem?.let { a ->
            DownloadSheet(
                cameraName = camera.displayName,
                alert = a,
                options = exportOptions,
                onOptions = onExportOptions,
                onDownload = {
                    showDownload = false
                    onDownloadAlert(a, camera.displayName)
                },
                onDismiss = { showDownload = false },
            )
        }
    }
    if (showGoTo) {
        GoToSheet(
            cameraName = camera.displayName,
            defaultAt = if (live) now() - 3_600_000L else pos,
            cache = monthCache,
            loadMonth = { start, end -> api.timeline(id, start, end, CalendarMath.STEP_MS) },
            onGo = { start, end ->
                val w = TimelineMath.goTo(start, end, now())
                if (w == null) {
                    note = "That time hasn't been recorded yet. Pick an earlier time."
                } else {
                    enterRecording(w.start, true)
                    window = w
                }
                showGoTo = false
            },
            onDismiss = { showGoTo = false },
        )
    }
}

private fun formatWhen(ms: Long): String = SimpleDateFormat("EEE MMM d, h:mm:ss a", Locale.getDefault()).format(java.util.Date(ms))

@Composable
internal fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = Sl.Accent,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, if (selected) accent else Sl.Line, shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) accent else Sl.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}


/**
 * Where each camera sits in a group's combined picture. Live: the stream's own headers, else the camera list's layout.
 * A recording: Blue Iris lays a group out again for playback (seen: a different camera order), so the stream's headers
 * are only used when they name the cameras in the camera list's order; otherwise no layout (no guessing, no jumping).
 */
private fun groupLayoutFor(group: Camera, stream: VideoStream, width: Int, height: Int, live: Boolean): GroupLayout? {
    val fromHeaders = GroupLayout.fromHeaders(stream.camListHeader, stream.recListHeader, width, height)
    val names = group.group
    val rects = group.rects
    val w = group.width ?: 0
    val h = group.height ?: 0
    val fromList = if (names != null && rects != null && names.size == rects.size && w > 0 && h > 0) GroupLayout(names, rects, w, h) else null
    if (live) return fromHeaders ?: fromList
    val sameOrder = fromHeaders != null && fromList != null &&
        fromHeaders.cams.map { it.lowercase() } == fromList.cams.map { it.lowercase() }
    return if (sameOrder) fromHeaders else null
}

/** The camera list's own layout of a group (names and boxes in the group's picture size), if it has one. */
private fun listLayoutOf(group: Camera): GroupLayout? {
    val names = group.group
    val rects = group.rects
    val w = group.width ?: 0
    val h = group.height ?: 0
    return if (names != null && rects != null && names.size == rects.size && w > 0 && h > 0) GroupLayout(names, rects, w, h) else null
}

/** Flag or protect the alert being watched: two switches, saved in Blue Iris so Blue Iris and UI3 show them too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkSheet(
    info: String,
    flagged: Boolean,
    protectedAlert: Boolean,
    busy: Boolean,
    message: String?,
    onToggleFlag: () -> Unit,
    onToggleProtect: () -> Unit,
    onDownload: (() -> Unit)?,
    onDone: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDone, containerColor = Sl.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(if (onDownload != null) "This alert" else "Flag or protect", style = MaterialTheme.typography.titleMedium)
            Text(info, style = MaterialTheme.typography.bodySmall, color = Sl.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            if (onDownload != null) {
                TextButton(onClick = onDownload) { Text("Download this alert (MP4)…") }
            }
            MarkRow("Flagged", "Marks the alert to come back to. The Flagged filter shows just these.", flagged, !busy, onToggleFlag)
            MarkRow("Protected", "Blue Iris keeps it when it clears out old alerts and recordings, like Protect in UI3.", protectedAlert, !busy, onToggleProtect)
            Text(
                message ?: "Saved in Blue Iris, so UI3 and Blue Iris show it too.",
                style = MaterialTheme.typography.bodySmall,
                color = if (message != null && message.contains("isn't allowed")) MaterialTheme.colorScheme.error else Sl.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun MarkRow(title: String, note: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = Sl.Muted)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}
