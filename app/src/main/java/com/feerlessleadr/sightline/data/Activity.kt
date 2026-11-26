package com.feerlessleadr.sightline.data

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Alerts per day, by kind. */
class DayCount(val startMs: Long, val total: Int, val byClass: Map<AlertClass, Int>)

/** One camera's alerts by hour of the day. */
class CameraHours(val camera: String, val hours: IntArray, val total: Int)

/**
 * The Activity overview worked out from a list of alerts: alerts per day stacked by kind, and a camera by hour-of-day count,
 * as in the web app. Each alert counts once, by its first label (AI or camera).
 */
class ActivityReport(val days: List<DayCount>, val cameras: List<CameraHours>, val hours: IntArray, val total: Int) {
    val busiestHour: Int get() = hours.indices.maxByOrNull { hours[it] } ?: 0
    val quietestHour: Int get() = hours.indices.minByOrNull { hours[it] } ?: 0

    companion object {
        /** [type] is a filter id (`all`, `people`, `vehicles`, `animals`); [days] counts back from today, which is the last entry. */
        fun build(items: List<AlertItem>, type: String, days: Int, nowMs: Long, zone: ZoneId): ActivityReport {
            val kept = items.filter { type == AlertFilters.ALL || AlertFilters.matches(type, it) }
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val starts = (days - 1 downTo 0).map { today.minusDays(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli() }
            val ends = starts.map { s -> Instant.ofEpochMilli(s).atZone(zone).plusDays(1).toInstant().toEpochMilli() }
            val perDay = starts.map { mutableMapOf<AlertClass, Int>() }
            val dayTotals = IntArray(days)
            val byCamera = LinkedHashMap<String, IntArray>()
            val all = IntArray(24)
            var total = 0
            for (a in kept) {
                val cls = AlertTags.of(a.memo, a.flags).first().cls
                val i = starts.indices.firstOrNull { a.timeMs >= starts[it] && a.timeMs < ends[it] }
                if (i != null) {
                    perDay[i][cls] = (perDay[i][cls] ?: 0) + 1
                    dayTotals[i]++
                }
                val hour = Instant.ofEpochMilli(a.timeMs).atZone(zone).hour
                byCamera.getOrPut(a.camera) { IntArray(24) }[hour]++
                all[hour]++
                total++
            }
            val rows = byCamera.entries.map { (cam, hrs) -> CameraHours(cam, hrs, hrs.sum()) }.sortedByDescending { it.total }
            return ActivityReport(starts.indices.map { DayCount(starts[it], dayTotals[it], perDay[it]) }, rows, all, total)
        }
    }
}

/** Alerts read for one choice of days, and when. */
class ActivityData(val items: List<AlertItem>, val atMs: Long)

data class ActivityUi(
    val days: Int = 7,
    val type: String = AlertFilters.ALL,
    val loading: Boolean = false,
    val daysDone: Int = 0,
    val data: Map<Int, ActivityData> = emptyMap(),
)

/**
 * Reads the alert list day by day (four requests at a time) for the Activity screen and keeps what it read for 10 minutes.
 * A day with 100 alerts, the most Blue Iris sends at once, is read on from its oldest alert so busy days are not undercounted.
 */
class ActivityFeed(
    private val scope: CoroutineScope,
    private val fetch: suspend (camera: String, startSec: Long, endSec: Long) -> List<AlertItem>,
    private val shown: (String) -> Boolean = { true },
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(ActivityUi())
    val state: StateFlow<ActivityUi> = _state.asStateFlow()
    private var generation = 0
    private var job: Job? = null

    fun open() = load()

    fun setDays(days: Int) {
        _state.update { it.copy(days = days) }
        load()
    }

    fun setType(type: String) = _state.update { it.copy(type = type) }

    fun clear() {
        generation++
        job?.cancel()
        _state.value = ActivityUi()
    }

    private fun load() {
        val days = _state.value.days
        val have = _state.value.data[days]
        if (have != null && nowMs() - have.atMs < FRESH_MS) {
            _state.update { it.copy(loading = false) }
            return
        }
        val gen = ++generation
        job?.cancel()
        _state.update { it.copy(loading = true, daysDone = 0) }
        job = scope.launch {
            val now = nowMs() / 1000
            val seen = HashSet<String>()
            val items = ArrayList<AlertItem>()
            try {
                for (chunk in (0 until days).chunked(PARALLEL)) {
                    val results = coroutineScope { chunk.map { d -> async { readDay(now, d) } }.awaitAll() }
                    if (gen != generation) return@launch
                    for (day in results) for (a in day) if (shown(a.camera) && seen.add(a.path)) items += a
                    _state.update { it.copy(daysDone = minOf(days, it.daysDone + chunk.size)) }
                }
            } catch (e: SessionExpiredException) {
                // The app has already gone back to the sign-in screen.
                if (gen == generation) _state.update { it.copy(loading = false) }
                return@launch
            }
            if (gen == generation) {
                _state.update { it.copy(loading = false, data = it.data + (days to ActivityData(items, nowMs()))) }
            }
        }
    }

    /** One day's alerts (day 0 is the last 24 hours); a day Blue Iris could not give counts as empty. */
    private suspend fun readDay(now: Long, d: Int): List<AlertItem> {
        val start = now - (d + 1) * DAY
        var end = now - d * DAY + if (d == 0) 60 else 0
        val out = ArrayList<AlertItem>()
        while (true) {
            val page = try {
                fetch("index", start, end)
            } catch (e: SessionExpiredException) {
                throw e
            } catch (e: BlueIrisException) {
                return out
            }
            out += page
            if (page.size < PAGE_CAP) return out
            val oldest = page.minOf { it.timeMs } / 1000
            if (oldest <= start || oldest >= end) return out
            end = oldest
        }
    }

    private companion object {
        const val DAY = 86_400L
        const val PARALLEL = 4
        const val PAGE_CAP = 100
        const val FRESH_MS = 600_000L
    }
}
