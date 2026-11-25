package com.feerlessleadr.sightline.video

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The stretch of time the timeline bar shows, in milliseconds since 1970. */
data class TimeWindow(val start: Long, val end: Long) {
    val length: Long get() = end - start

    /** Time at horizontal position [x] of a bar [width] wide. */
    fun timeAt(x: Float, width: Float): Long = start + (x / width * length).toLong()

    /** Horizontal position of [time] on a bar [width] wide. */
    fun xOf(time: Long, width: Float): Float = (time - start).toFloat() / length * width
}

/** The numbers behind the timeline, the same as the web app's, kept apart from the screen so they can be tested. */
object TimelineMath {
    /** Blue Iris will not serve the most recent ~15 seconds from its timeline. */
    const val KEEP_OUT_MS = 15_000L
    const val MIN_WINDOW_MS = 30_000L
    const val MAX_WINDOW_MS = 7L * 86_400_000L

    /** The zoom buttons: window length and its label. */
    val ZOOMS = listOf(
        120_000L to "2m", 900_000L to "15m", 3_600_000L to "1h", 21_600_000L to "6h", 86_400_000L to "24h", 172_800_000L to "48h",
    )

    val SPEEDS = listOf(1, 2, 4, 8, 16)

    /** How long one frame lasts, from the camera's frame rate; 10 frames a second if it is not known (as the web app does). */
    fun frameIntervalMs(fps: Double?): Long = (1000.0 / (fps?.takeIf { it > 0 } ?: 10.0)).roundToLong().coerceAtLeast(1L)

    /** Where a frame step from [position] lands, or null if it would go past the newest playable moment (no stepping into live). */
    fun stepTarget(position: Long, direction: Int, intervalMs: Long, now: Long): Long? {
        val target = position + direction * intervalMs
        return if (direction > 0 && target > latestPlayable(now)) null else target
    }

    /** The newest moment that can be played back from the timeline. */
    fun latestPlayable(now: Long) = now - KEEP_OUT_MS

    fun clampLength(length: Long) = length.coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS)

    /** How many milliseconds one timeline step stands for: a power of two giving about 500 steps across the window. */
    fun msecPerStep(windowLength: Long): Long {
        val power = max(6, (ln(windowLength / 500.0) / ln(2.0)).roundToInt())
        return 2.0.pow(power).toLong()
    }

    /** Spacing of the time labels under the bar: the smallest step that gives six labels or fewer. */
    fun labelStep(windowLength: Long): Long {
        val steps = longArrayOf(
            5_000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 900_000, 1_800_000, 3_600_000,
            7_200_000, 10_800_000, 21_600_000, 43_200_000, 86_400_000,
        )
        for (s in steps) if (windowLength.toDouble() / s <= 6) return s
        return 172_800_000L
    }

    /** Live: the window ends a little in the future so "now" is not at the very edge. */
    fun liveWindow(length: Long, now: Long): TimeWindow {
        val end = now + (length * 0.04).toLong()
        return TimeWindow(end - length, end)
    }

    /** A window of [length] centred on [center], but not reaching more than 4% past [now]. */
    fun centered(center: Long, length: Long, now: Long): TimeWindow {
        val end = min(center + length / 2, now + (length * 0.04).toLong())
        return TimeWindow(end - length, end)
    }

    /** True when the playhead is near the right edge and the window can still move on. */
    fun shouldFollow(position: Long, window: TimeWindow, now: Long): Boolean =
        position > window.start + window.length * 0.88 && window.end < now + window.length * 0.02

    /** The window after pinching: [factor] above 1 shows less time (zoom in), around the time [anchor]. */
    fun zoomed(window: TimeWindow, factor: Float, anchor: Long, now: Long): TimeWindow {
        val length = clampLength((window.length / factor).toLong())
        val ratio = (anchor - window.start).toDouble() / window.length
        var start = anchor - (length * ratio).toLong()
        var end = start + length
        val limit = now + (length * 0.04).toLong()
        if (end > limit) {
            end = limit
            start = end - length
        }
        return TimeWindow(start, end)
    }

    enum class QuickRange(val label: String) {
        LastHour("Last hour"),
        ThisMorning("This morning"),
        LastNight("Last night"),
        Yesterday("Yesterday"),
        ThisTimeYesterday("This time yesterday"),
        ThisTimeLastWeek("This time last week"),
    }

    /** The quick choices of "go to date and time". [dayStart] is midnight today in the phone's time zone. */
    fun quickRange(kind: QuickRange, now: Long, dayStart: Long): Pair<Long, Long> {
        val hour = 3_600_000L
        return when (kind) {
            QuickRange.LastHour -> (now - hour) to now
            QuickRange.ThisMorning -> (dayStart + 6 * hour) to (dayStart + 12 * hour)
            QuickRange.LastNight -> (dayStart - 4 * hour) to (dayStart + 6 * hour)
            QuickRange.Yesterday -> (dayStart - 24 * hour) to (dayStart - 1000)
            QuickRange.ThisTimeYesterday -> (now - 24 * hour - hour / 2) to (now - 24 * hour + hour / 2)
            QuickRange.ThisTimeLastWeek -> (now - 168 * hour - hour / 2) to (now - 168 * hour + hour / 2)
        }
    }

    /**
     * The window for "go to" a range, or null if that time has not been recorded yet. Playing starts at the range's start.
     * The window is at least 30 s and at most 7 days long.
     */
    fun goTo(startMs: Long, endMs: Long, now: Long): TimeWindow? {
        if (startMs > latestPlayable(now) - 30_000L) return null
        val e = min(endMs, latestPlayable(now))
        val s = min(startMs, e - 30_000L)
        return TimeWindow(s, s + clampLength(e - s))
    }
}
