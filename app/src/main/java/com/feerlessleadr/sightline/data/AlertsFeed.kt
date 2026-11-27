package com.feerlessleadr.sightline.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the list ended, for the line under it. */
enum class AlertsEnd { None, RangeEnd, QuietWeek, Failed }

/** What the Alerts tab shows. */
data class AlertsUi(
    val items: List<AlertItem> = emptyList(),
    /** Selected filter buttons (see [AlertFilters]); empty means All. */
    val filters: List<String> = emptyList(),
    /** A camera's short name, or `index` for all cameras. */
    val camera: String = "index",
    val range: AlertRange? = null,
    val loading: Boolean = false,
    val loadedOnce: Boolean = false,
    val exhausted: Boolean = false,
    val end: AlertsEnd = AlertsEnd.None,
    val error: String? = null,
    /** Flags changed on this phone, by record id, until the list is loaded again. */
    val flagOverrides: Map<String, Long> = emptyMap(),
    /** Counts how many times the list was started again (a new filter, camera or time), so the screen can go back to the top. */
    val reloads: Int = 0,
) {
    fun flagsOf(alert: AlertItem): Long = flagOverrides[AlertFlags.recordId(alert.path)] ?: alert.flags
}

/**
 * The alert list: newest first, loaded a day at a time going back (Blue Iris answers with at most 100 alerts per
 * request), and filtered here on the phone, as the web app does. [fetch] asks Blue Iris for the alerts of [camera]
 * between two times (seconds since 1970); [shown] says whether a camera is one the app shows.
 */
class AlertsFeed(
    private val scope: CoroutineScope,
    private val fetch: suspend (camera: String, startSec: Long, endSec: Long) -> List<AlertItem>,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val shown: (String) -> Boolean = { true },
    private val retryDelayMs: Long = 1_500L,
) {
    private val _state = MutableStateFlow(AlertsUi())
    val state: StateFlow<AlertsUi> = _state.asStateFlow()

    private var generation = 0
    private var cursorSec = 0L
    private val seen = HashSet<String>()
    private var job: Job? = null
    private var loadedAtMs = 0L

    /** Forgets everything (signing out or locking). */
    fun clear() {
        generation++
        job?.cancel()
        seen.clear()
        loadedAtMs = 0L
        _state.value = AlertsUi()
    }

    /** Loads the first time, and again when the list is old (the tab being shown). */
    fun ensureLoaded() {
        if (!_state.value.loadedOnce || nowMs() - loadedAtMs > STALE_MS) reload()
    }

    /** Starts again from now (or from the end of the chosen range). */
    fun reload() {
        generation++
        job?.cancel()
        val range = _state.value.range
        cursorSec = range?.endSec ?: (nowMs() / 1000 + 60)
        seen.clear()
        _state.update { it.copy(items = emptyList(), loading = false, exhausted = false, end = AlertsEnd.None, error = null, flagOverrides = emptyMap(), reloads = it.reloads + 1) }
        loadMore()
    }

    /** Selects a filter: just that one, or (with [combine]) added to or taken from the others. All clears. */
    fun select(id: String, combine: Boolean) {
        val current = _state.value.filters
        val next = when {
            id == AlertFilters.ALL -> emptyList()
            combine -> if (id in current) current - id else current + id
            else -> listOf(id)
        }
        if (next == current) return
        _state.update { it.copy(filters = next) }
        reload()
    }

    /** Drops selected filters that are no longer among the buttons shown, and reloads if any went. */
    fun retainFilters(allowed: List<String>) {
        val current = _state.value.filters
        val keep = current.filter { id -> allowed.any { sameFilter(it, id) } }
        if (keep.size == current.size) return
        _state.update { it.copy(filters = keep) }
        if (_state.value.loadedOnce) reload()
    }

    fun setCamera(camera: String) {
        if (camera == _state.value.camera) return
        _state.update { it.copy(camera = camera) }
        reload()
    }

    fun setRange(range: AlertRange?) {
        _state.update { it.copy(range = range) }
        reload()
    }

    /** Shows [flags] for the alert at [path] straight away (a change made here), or takes it back with [revertTo]. */
    fun noteFlags(path: String, flags: Long) {
        val id = AlertFlags.recordId(path)
        if (id.isEmpty()) return
        _state.update { it.copy(flagOverrides = it.flagOverrides + (id to flags)) }
    }

    /** More, older alerts (the next day or days back). */
    fun loadMore() {
        val s = _state.value
        if (s.loading || s.exhausted) return
        val gen = generation
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch { loadPage(gen) }
    }

    private suspend fun loadPage(gen: Int) {
        var added = 0
        var emptyDays = 0
        try {
            while (added == 0 && emptyDays < 7 && !atRangeStart()) {
                val s = _state.value
                val range = s.range
                val start = if (range != null) maxOf(range.startSec, cursorSec - DAY_SEC) else cursorSec - DAY_SEC
                val raw = query(s.camera, start, cursorSec).filter { seen.add(it.path) }.sortedByDescending { it.timeMs }
                if (gen != generation) return
                // If Blue Iris capped the answer, carry on from the oldest alert; otherwise the whole day is covered.
                cursorSec = if (raw.size >= PAGE_CAP) maxOf(start, raw.last().timeMs / 1000) else start
                val keep = raw.filter { shown(it.camera) && AlertFilters.matchesAny(s.filters, it, _state.value.flagsOf(it)) &&
                    (range == null || (it.timeMs / 1000 in range.startSec..range.endSec)) }
                if (keep.isEmpty()) emptyDays++
                added += keep.size
                _state.update { it.copy(items = it.items + keep) }
            }
            loadedAtMs = nowMs()
            _state.update {
                when {
                    atRangeStart() -> it.copy(exhausted = true, end = AlertsEnd.RangeEnd)
                    added == 0 -> it.copy(exhausted = true, end = AlertsEnd.QuietWeek)
                    else -> it
                }.copy(loading = false, loadedOnce = true)
            }
        } catch (e: SessionExpiredException) {
            // The app has already gone back to the sign-in screen.
            if (gen == generation) _state.update { it.copy(loading = false, exhausted = true) }
        } catch (e: BlueIrisException) {
            if (gen == generation) _state.update { it.copy(loading = false, exhausted = true, end = AlertsEnd.Failed, error = e.message) }
        }
    }

    /** "Look further back" after a quiet week: carry on past the empty stretch. */
    fun lookFurtherBack() {
        _state.update { it.copy(exhausted = false, end = AlertsEnd.None) }
        loadMore()
    }

    /** After a failure: try the same step again. */
    fun retry() {
        _state.update { it.copy(exhausted = false, end = AlertsEnd.None, error = null) }
        loadMore()
    }

    private fun atRangeStart(): Boolean {
        val range = _state.value.range ?: return false
        return cursorSec <= range.startSec
    }

    /** Blue Iris sometimes refuses an alert list without saying why; wait a moment and ask once more. */
    private suspend fun query(camera: String, startSec: Long, endSec: Long): List<AlertItem> {
        try {
            return fetch(camera, startSec, endSec)
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: BlueIrisException) {
            delay(retryDelayMs)
            return try {
                fetch(camera, startSec, endSec)
            } catch (again: SessionExpiredException) {
                throw again
            } catch (again: BlueIrisException) {
                throw BlueIrisException("Could not load alerts: " + (again.message ?: "Blue Iris gave no reason"))
            }
        }
    }

    private companion object {
        const val DAY_SEC = 86_400L
        const val PAGE_CAP = 100
        const val STALE_MS = 5 * 60_000L
    }
}
