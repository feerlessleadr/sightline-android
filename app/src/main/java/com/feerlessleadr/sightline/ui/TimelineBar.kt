package com.feerlessleadr.sightline.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import com.feerlessleadr.sightline.data.AlertClass
import com.feerlessleadr.sightline.data.AlertItem
import com.feerlessleadr.sightline.data.AlertMark
import com.feerlessleadr.sightline.data.AlertTags
import com.feerlessleadr.sightline.data.Timeline
import com.feerlessleadr.sightline.video.TimeWindow
import com.feerlessleadr.sightline.video.TimelineMath
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val CoverageColor = Color(0xFF2B4D61)

/** The class of an alert mark: the alert list's AI label if one lines up with it, else Blue Iris's own flag bits. */
private fun markClass(mark: AlertMark, alerts: List<AlertItem>): AlertClass {
    for (a in alerts) {
        if (a.timeMs >= mark.startMs - 3000 && a.timeMs <= mark.startMs + max(3000L, mark.lengthMs)) {
            return AlertTags.of(a.memo, a.flags).first().cls
        }
    }
    return AlertTags.flagClass(mark.flags)
}

/**
 * The timeline under the picture: where there are recordings (blue) and alerts (their colours), time labels, and the
 * playhead. One finger scrubs ([onScrub] with the time under the finger, [onScrubEnd] on letting go), two fingers
 * pinch ([onZoom] with the change in zoom and the time between the fingers). [positionMs] null means live: the
 * playhead sits at [nowMs].
 */
@Composable
fun TimelineBar(
    window: TimeWindow,
    timeline: Timeline,
    alerts: List<AlertItem>,
    positionMs: Long?,
    nowMs: Long,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onZoom: (factor: Float, anchor: Long) -> Unit,
    modifier: Modifier = Modifier,
    selection: LongRange? = null,
) {
    val measurer = rememberTextMeasurer()
    val marks = remember(timeline, alerts) { timeline.alerts.map { it to markClass(it, alerts) } }
    val scrub by rememberUpdatedState(onScrub)
    val scrubEnd by rememberUpdatedState(onScrubEnd)
    val zoom by rememberUpdatedState(onZoom)
    val win by rememberUpdatedState(window)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .semantics {
                contentDescription = "Timeline"
                stateDescription = if (positionMs == null) "Live" else SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(Date(positionMs))
                // A screen reader cannot drag along the timeline, so it gets steps back and forward instead.
                val here = positionMs ?: nowMs
                fun step(label: String, deltaMs: Long) = CustomAccessibilityAction(label) {
                    scrub((here + deltaMs).coerceAtMost(nowMs))
                    scrubEnd()
                    true
                }
                customActions = listOf(
                    step("Back 15 seconds", -15_000L), step("Forward 15 seconds", 15_000L),
                    step("Back 5 minutes", -300_000L), step("Forward 5 minutes", 300_000L),
                    step("Back 1 hour", -3_600_000L), step("Go to live", Long.MAX_VALUE / 4),
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    var lastDistance = 0f
                    val width = size.width.toFloat()
                    scrub(win.timeAt(first.position.x, width))
                    first.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            zooming = true
                            val a = down[0].position
                            val b = down[1].position
                            val distance = abs(a.x - b.x).coerceAtLeast(1f)
                            if (lastDistance > 0f) zoom(distance / lastDistance, win.timeAt((a.x + b.x) / 2f, width))
                            lastDistance = distance
                            event.changes.forEach { it.consume() }
                        } else if (down.size == 1 && !zooming) {
                            scrub(win.timeAt(down[0].position.x.coerceIn(0f, width), width))
                            down[0].consume()
                        } else if (down.isEmpty() || event.changes.all { it.changedToUp() }) {
                            break
                        } else {
                            lastDistance = 0f // one finger left after a pinch: wait for all to lift
                        }
                    }
                    if (!zooming) scrubEnd()
                }
            },
    ) {
        val w = size.width
        val trackTop = 10.dp.toPx()
        val trackHeight = 22.dp.toPx()
        val round = CornerRadius(6.dp.toPx())
        fun x(t: Long) = window.xOf(t, w)
        drawRoundRect(Sl.Surface2, Offset(0f, trackTop), Size(w, trackHeight), round)
        // Recorded coverage.
        for (c in timeline.clips) {
            val x1 = x(c.startMs)
            val x2 = x(c.startMs + c.lengthMs)
            if (x2 < 0f || x1 > w) continue
            val left = x1.coerceAtLeast(0f)
            drawRect(CoverageColor, Offset(left, trackTop), Size(max(1f, minOf(x2, w) - left), trackHeight))
        }
        // Alerts.
        for ((a, cls) in marks) {
            val x1 = x(a.startMs)
            val x2 = x(a.startMs + a.lengthMs)
            if (x2 < 0f || x1 > w) continue
            val left = x1.coerceAtLeast(0f)
            drawRect(classColor(cls), Offset(left, trackTop + 4.dp.toPx()), Size(max(3.dp.toPx(), minOf(x2, w) - left), trackHeight - 8.dp.toPx()))
        }
        // The stretch chosen for download: a tinted band with a bar at each end.
        if (selection != null) {
            val x1 = x(selection.first).coerceIn(0f, w)
            val x2 = x(selection.last).coerceIn(0f, w)
            if (x2 > x1) drawRect(Sl.Accent.copy(alpha = 0.35f), Offset(x1, trackTop), Size(x2 - x1, trackHeight))
            for (edge in listOf(selection.first, selection.last)) {
                val ex = x(edge)
                if (ex in 0f..w) drawRect(Sl.Accent, Offset(ex - 1.5.dp.toPx(), trackTop - 2.dp.toPx()), Size(3.dp.toPx(), trackHeight + 4.dp.toPx()))
            }
        }
        // The future: nothing is recorded there.
        val xNow = x(nowMs)
        if (xNow < w) drawRect(Sl.Background, Offset(xNow.coerceAtLeast(0f), trackTop), Size(w - xNow.coerceAtLeast(0f), trackHeight))
        // Time labels.
        val step = TimelineMath.labelStep(window.length)
        val zone = Calendar.getInstance().timeZone
        val offset = zone.getOffset(window.start)
        val first = Math.ceil((window.start + offset).toDouble() / step).toLong() * step - offset
        val style = TextStyle(color = Sl.Faint, fontSize = 10.5.sp)
        val withSeconds = SimpleDateFormat("h:mm:ss", Locale.getDefault())
        val minutes = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dayName = SimpleDateFormat("EEE", Locale.getDefault())
        var t = first
        while (t <= window.end) {
            val tx = x(t)
            if (tx >= 14.dp.toPx() && tx <= w - 14.dp.toPx()) {
                drawRect(Sl.Faint, Offset(tx, trackTop + trackHeight + 2.dp.toPx()), Size(1f, 4.dp.toPx()))
                val date = Date(t)
                val cal = Calendar.getInstance().apply { time = date }
                val midnight = step >= 3_600_000L && cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0
                val label = when {
                    midnight -> dayName.format(date)
                    step < 60_000L -> withSeconds.format(date)
                    else -> minutes.format(date).lowercase()
                }
                val layout = measurer.measure(label, style)
                drawText(layout, topLeft = Offset(tx - layout.size.width / 2f, trackTop + trackHeight + 8.dp.toPx()))
            }
            t += step
        }
        // Playhead.
        val headX = x(positionMs ?: nowMs)
        if (headX in 0f..w) {
            val headColor = if (positionMs == null) Sl.Live else Color.White
            drawRect(headColor, Offset(headX - 1.dp.toPx(), trackTop - 5.dp.toPx()), Size(2.dp.toPx(), trackHeight + 10.dp.toPx()))
            drawCircle(headColor, 4.dp.toPx(), Offset(headX, trackTop - 5.dp.toPx()))
        }
    }
}
