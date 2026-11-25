package com.feerlessleadr.sightline.ui

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.content.res.Configuration
import android.graphics.Matrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
    val openLive: suspend (camera: String, size: StreamRequest?, audio: Boolean) -> VideoStream,
    val openRecorded: suspend (camera: String, positionMs: Long, speedPercent: Int, size: StreamRequest?, audio: Boolean) -> VideoStream,
    val openClip: suspend (file: String, offsetMs: Long, speedPercent: Int, size: StreamRequest?, audio: Boolean) -> VideoStream,
    val liveStill: suspend (camera: String, width: Int) -> ByteArray?,
    val recordedStill: suspend (camera: String, positionMs: Long, width: Int) -> ByteArray,
    val timeline: suspend (camera: String, startMs: Long, endMs: Long, msecPerStep: Long) -> Timeline,
    val alerts: suspend (camera: String, startMs: Long, endMs: Long) -> List<AlertItem>,
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
    soundOn: Boolean,
    volume: Float,
    onSound: (on: Boolean, volume: Float) -> Unit,
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
    var still by remember(id) { mutableStateOf<ImageBitmap?>(null) }
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

    // Digital zoom (pinch the picture, drag to pan, double tap to reset). In Auto quality a zoomed picture asks Blue
    // Iris for a sharper stream once the zoom has settled, like the web app.
    var zoom by remember(id) { mutableFloatStateOf(1f) }
    var panX by remember(id) { mutableFloatStateOf(0f) }
    var panY by remember(id) { mutableFloatStateOf(0f) }
    var boost by remember(id) { mutableFloatStateOf(1f) }
    var fill by remember(id) { mutableStateOf(false) } // double tap: fit the whole picture, or fill the screen
    var fullScreen by remember { mutableStateOf(false) }
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
        val file = AlertClips.fileFor(target.clip, target.path)
        val alertClip = AlertClip(file, target.timeMs - target.offsetMs, target.timeMs + AlertClips.durationMillis(target.fileSize))
        clip = alertClip
        enterRecording(max(target.timeMs - 2000, alertClip.start), true)
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
    LaunchedEffect(surface, live, playing, scrubbing, serial, streamSize, attempt, id, askAudio) {
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
                isLive -> api.openLive(id, size, askAudio)
                fileClip != null -> try {
                    api.openClip(fileClip.file, startAt - fileClip.start, atSpeed * 100, size, askAudio && atSpeed == 1).also { fromFile = true }
                } catch (e: SessionExpiredException) {
                    throw e
                } catch (e: BlueIrisException) {
                    clip = fileClip.copy(broken = true) // do not try this file again
                    api.openRecorded(id, startAt, atSpeed * 100, size, askAudio && atSpeed == 1)
                }
                else -> api.openRecorded(id, startAt, atSpeed * 100, size, askAudio && atSpeed == 1)
            }
            val connected = SystemClock.elapsedRealtime() - started
            status = "Waiting for the first picture…"
            var firstPicture = -1L
            playLive(
                stream, target,
                onSize = { w, h -> aspect = w.toFloat() / h.toFloat() },
                onStats = { s ->
                    if (s.shown > 0 && firstPicture < 0) firstPicture = SystemClock.elapsedRealtime() - started
                    stats = "${if (isLive) "live" else if (fromFile) "alert file x$atSpeed" else "recorded x$atSpeed"}: $s; asked for ${size.width}x${size.height} ${size.kbps} kbps; " +
                        "connection opened after $connected ms, first picture $firstPicture ms after the request started"
                    if (s.shown > 0) {
                        status = ""
                        streaming = true
                        still = null
                    }
                },
                onFrameTime = { frameUtc.set(it) },
                speed = if (isLive) null else atSpeed.toFloat(),
                clipBaseMs = if (fromFile) startAt else null,
                audio = sound,
                onHasAudio = { streamHasAudio = it },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: StreamEndedException) {
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
                val bytes = api.recordedStill(id, pos, stillWidth)
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
    val onStageTap by rememberUpdatedState<() -> Unit> {
        lastTouch = SystemClock.elapsedRealtime()
        when {
            overlay -> controlsVisible = !controlsVisible
            live -> onBack()
            else -> togglePlay()
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
    val qualityButton: @Composable () -> Unit = {
        TextButton(onClick = { pickQuality = true }) { Text(quality.label.uppercase()) }
    }

    // The stage takes all the room it is given. The picture sits in its middle; zooming grows into the whole stage.
    val stage: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier
                .background(Color.Black)
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
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                endedAt = event.changes.maxOf { it.uptimeMillis }
                                if (fingers >= 2) multi = true
                                val mine = event.changes.firstOrNull { it.id == down.id }
                                if (mine != null && (mine.position - down.position).getDistance() > slop) moved = true
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
                                        val fit = ZoomMath.fit(w, h, aspect, fill)
                                        panX = ZoomMath.clampAxis(fit[0], fit[2], w, next, centre.x - (centre.x - panX) * k + pan.x)
                                        panY = ZoomMath.clampAxis(fit[1], fit[3], h, next, centre.y - (centre.y - panY) * k + pan.y)
                                        zoom = next
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (!moved && !multi && endedAt - down.uptimeMillis < 400L) {
                                if (pendingTap != null && endedAt - lastTapAt < 300L) {
                                    pendingTap?.cancel()
                                    pendingTap = null
                                    lastTapAt = 0L
                                    onStageDoubleTap()
                                } else {
                                    lastTapAt = endedAt
                                    pendingTap = launch {
                                        delay(300)
                                        pendingTap = null
                                        onStageTap()
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            // The video view fills the stage; a matrix inside it fits the picture and applies the zoom, so the
            // video is never scaled by the layout system (which made it go black).
            AndroidView(
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
                },
            )
            // Covers the video only while no video frame is showing (a paused recording, or the start of a stream).
            if (!streaming) {
                still?.let {
                    Image(
                        it, contentDescription = null, contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX
                            translationY = panY
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                    )
                }
            }
            // Small labels on the picture; they move down while the floating top bar is showing.
            val labelTop = if (overlay && controlsVisible) 52.dp else 0.dp
            Text(
                if (live) "Live" else formatWhen(pos),
                color = if (live) Sl.Live else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = labelTop)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
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
            if (status.isNotEmpty() && error == null && !streaming && (live || playing) && !scrubbing) {
                Text(
                    status,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            // Messages sit over the bottom of the picture so they never push the controls around.
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
            Pill("−10 s") { seek((if (live) TimelineMath.latestPlayable(now()) else pos) - 10_000L) }
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Sl.Surface2)
                    .clickable(role = Role.Button, onClickLabel = if (live || playing) "Pause" else "Play") { togglePlay() },
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(if (live || playing) Glyph.Pause else Glyph.Play, Sl.Text, 28.dp) }
            Pill("+10 s") { if (!live) seek(pos + 10_000L) }
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
        Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                    Text(camera.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
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
                    title = { Text(camera.displayName) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                    actions = {
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
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, if (selected) accent else Sl.Line, shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) accent else Sl.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

