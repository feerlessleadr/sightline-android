package com.feerlessleadr.sightline.data

import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How far back a camera's recordings go, counted back from the newest one until a gap of more than 2 days, so an old
 * protected clip does not make a camera look as if it keeps months. [none] means no recordings were found.
 */
class Retention(val fromMs: Long, val hours: Double, val older: Boolean, val oldestMs: Long, val none: Boolean = false) {
    companion object {
        const val GAP_MS = 2 * 86_400_000L

        val None = Retention(0, 0.0, false, 0, none = true)

        fun of(clips: List<TimeSpan>): Retention {
            if (clips.isEmpty()) return None
            val segs = clips.map { it.startMs to it.startMs + it.lengthMs }.sortedBy { it.first }
            var i = segs.lastIndex
            var from = segs[i].first
            while (i > 0 && from - segs[i - 1].second < GAP_MS) {
                i--
                from = minOf(from, segs[i].first)
            }
            val hours = segs.drop(i).sumOf { it.second - it.first } / 3_600_000.0
            return Retention(from, hours, i > 0, segs.first().first)
        }

        /** "36 h" or "12 days": how long ago the camera's footage starts. */
        fun kept(ms: Long): String {
            val h = ms / 3_600_000.0
            return if (h < 48) "${maxOf(1, Math.round(h))} h" else "${Math.round(h / 24)} days"
        }

        /** The Footage kept tile: the range across cameras, as in the web app ("12–30 days", "14 days", "20–40 hours"). */
        fun range(items: Collection<Retention>, nowMs: Long): String? {
            val days = items.filter { !it.none }.map { (nowMs - it.fromMs) / 86_400_000.0 }
            if (days.isEmpty()) return null
            val lo = days.min()
            val hi = days.max()
            fun span(a: Long, b: Long, unit: String) = (if (a == b) "$a" else "$a–$b") + " $unit"
            return if (hi < 2) {
                span(Math.round(lo * 24), Math.round(hi * 24), "hours")
            } else {
                val low = if (lo < 2) String.format(Locale.ROOT, "%.1f", lo) else Math.round(lo).toString()
                val high = Math.round(hi).toString()
                (if (low == high) low else "$low–$high") + " days"
            }
        }
    }
}

data class RetentionUi(val byCamera: Map<String, Retention> = emptyMap(), val busy: Boolean = false)

/**
 * Reads, one camera at a time, how much footage each camera keeps: a single wide, coarse timeline request per camera
 * (400 days at one hour per step). The answer is kept for 10 minutes. [timeline] asks Blue Iris for a camera's recordings
 * between two times (milliseconds) at that many milliseconds per step.
 */
class RetentionFeed(
    private val scope: CoroutineScope,
    private val timeline: suspend (camera: String, startMs: Long, endMs: Long, msecPerStep: Long) -> Timeline,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(RetentionUi())
    val state: StateFlow<RetentionUi> = _state.asStateFlow()
    private var job: Job? = null
    private var atMs = 0L

    fun clear() {
        job?.cancel()
        atMs = 0L
        _state.value = RetentionUi()
    }

    /** Reads [cameras] (short names) unless it was done in the last 10 minutes. */
    fun refresh(cameras: List<String>) {
        if (_state.value.busy || (atMs != 0L && nowMs() - atMs < FRESH_MS)) return
        _state.update { it.copy(busy = true) }
        job = scope.launch {
            val found = LinkedHashMap<String, Retention>()
            var finished = false
            try {
                val now = nowMs()
                for (camera in cameras) {
                    try {
                        found[camera] = Retention.of(timeline(camera, now - DAYS * 86_400_000L, now, STEP_MS).clips)
                    } catch (e: SessionExpiredException) {
                        throw e
                    } catch (e: BlueIrisException) {
                        // This camera could not be read; the rest still can.
                    }
                    _state.update { it.copy(byCamera = found.toMap()) }
                }
                finished = true
            } catch (e: SessionExpiredException) {
                // The app has already gone back to the sign-in screen.
            } finally {
                if (finished) atMs = nowMs()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        const val DAYS = 400L
        const val STEP_MS = 3_600_000L
        const val FRESH_MS = 600_000L
    }
}
