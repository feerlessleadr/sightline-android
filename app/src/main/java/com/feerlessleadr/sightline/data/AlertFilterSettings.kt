package com.feerlessleadr.sightline.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One filter seen lately and how many alerts it matched. */
@Serializable
data class RecentFilter(val id: String, val n: Int)

/**
 * Which filter buttons the Alerts tab shows. Two modes, as in the web app: Choose (exactly the buttons picked) and Auto
 * (All, the ones always wanted, then the most common detections of the last 3 weeks).
 */
@Serializable
data class AlertFilterConfig(
    val auto: Boolean = false,
    /** Choose mode: the buttons shown. */
    val shown: List<String> = AlertFilters.DEFAULT,
    /** Auto mode: the buttons always shown after All. */
    val pins: List<String> = emptyList(),
    /** Auto mode: how many recent ones to add; 0 fits the screen (up to [AUTO_MAX]). */
    val count: Int = 0,
    /** Labels typed in by the owner (lower case). */
    val labels: List<String> = emptyList(),
    val recent: List<RecentFilter> = emptyList(),
    val recentAtMs: Long = 0,
) {
    /** The buttons, in order. */
    fun chips(): List<String> {
        if (!auto) return shown
        val extra = recent.map { it.id }.filter { id -> pins.none { sameFilter(it, id) } }
        return listOf(AlertFilters.ALL) + pins + extra.take(if (count == 0) AUTO_MAX else count)
    }

    /** The list the settings sheet edits: the pins in Auto, the shown buttons in Choose. */
    fun active(): List<String> = if (auto) pins else shown

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val AUTO_MAX = 5
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(text: String?): AlertFilterConfig =
            if (text.isNullOrBlank()) AlertFilterConfig() else try { json.decodeFromString(serializer(), text) } catch (e: Exception) { AlertFilterConfig() }
    }
}

/** True if two filters are the same button: equal, or labels that are the same word ignoring case and plurals. */
fun sameFilter(a: String, b: String): Boolean =
    a == b || (a.startsWith("label:") && b.startsWith("label:") && AlertFilters.normLabel(a.removePrefix("label:")) == AlertFilters.normLabel(b.removePrefix("label:")))

/** A label seen on the cameras: how it is written and how many alerts carried it. */
data class LabelCount(val name: String, val n: Int)

/** Counts the AI labels in a stretch of alerts (the scan behind Auto and the label list). */
class LabelTally {
    val counts = LinkedHashMap<String, LabelCount>()
    private val ids = HashMap<String, Int>()
    private val idOf = HashMap<String, String>()

    fun add(alerts: List<AlertItem>) {
        for (alert in alerts) {
            val mine = HashSet<String>()
            for (n in AlertFilters.memoNames(alert.memo).toSet()) {
                val alias = AlertTags.onvifAlias(n)
                val key = if (alias != null) alias.text.lowercase().replace(" ", "") else AlertFilters.normLabel(n)
                if (key.isEmpty() || key == "motion") continue
                val shownName = alias?.text?.lowercase() ?: n.replace(Regex("[_-]+"), " ")
                val old = counts[key]
                counts[key] = LabelCount(old?.name ?: shownName, (old?.n ?: 0) + 1)
                val id = recentId(n) ?: continue
                mine += if (id.startsWith("label:")) idOf.getOrPut(key) { id } else id
            }
            if (AlertFlags.has(alert.flags, 26)) mine += AlertFilters.PEOPLE
            if (AlertFlags.has(alert.flags, 27)) mine += AlertFilters.VEHICLES
            mine.forEach { ids[it] = (ids[it] ?: 0) + 1 }
        }
    }

    /** The filters seen, most common first (up to 20). */
    fun recent(): List<RecentFilter> = ids.entries.sortedByDescending { it.value }.take(20).map { RecentFilter(it.key, it.value) }

    companion object {
        /** Which filter button an AI label belongs to: person to People, car and the like to Vehicles, anything else its own label. */
        fun recentId(name: String): String? {
            val alias = AlertTags.onvifAlias(name)
            if (alias != null) {
                return when {
                    alias.text == "Person" || alias.text == "Face" -> AlertFilters.PEOPLE
                    alias.cls == AlertClass.Vehicle -> AlertFilters.VEHICLES
                    alias.cls == AlertClass.Animal -> AlertFilters.ANIMALS
                    alias.cls == AlertClass.Motion -> null
                    else -> AlertFilters.label(alias.text)
                }
            }
            val k = AlertFilters.normLabel(name)
            if (k.isEmpty()) return null
            if (k == "person" || k == "people" || k == "face") return AlertFilters.PEOPLE
            if (AlertFilters.vehicleWord(k)) return AlertFilters.VEHICLES
            return AlertFilters.label(name.replace(Regex("[_-]+"), " "))
        }
    }
}

/** What the filter settings sheet shows. */
data class FilterSettingsUi(
    val config: AlertFilterConfig = AlertFilterConfig(),
    val scanning: Boolean = false,
    /** Labels seen in the last 3 weeks, by normalised name; null until a scan has finished. */
    val counts: Map<String, LabelCount>? = null,
)

/**
 * The owner's choice of filter buttons, and the scan of the last 3 weeks of alerts that Auto and the label list use.
 * [load] and [save] keep the choice on the phone.
 */
class AlertFilterSettings(
    private val scope: CoroutineScope,
    private val fetch: suspend (camera: String, startSec: Long, endSec: Long) -> List<AlertItem>,
    private val shown: (String) -> Boolean = { true },
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val load: () -> AlertFilterConfig = { AlertFilterConfig() },
    private val save: (AlertFilterConfig) -> Unit = {},
) {
    private val _state = MutableStateFlow(FilterSettingsUi(config = load()))
    val state: StateFlow<FilterSettingsUi> = _state.asStateFlow()

    private var job: Job? = null
    private var scanAtMs = 0L

    val config: AlertFilterConfig get() = _state.value.config

    private fun change(f: (AlertFilterConfig) -> AlertFilterConfig) {
        _state.update { it.copy(config = f(it.config)) }
        save(_state.value.config)
    }

    fun setAuto(auto: Boolean) {
        change { it.copy(auto = auto) }
        if (auto) scan()
    }

    fun setCount(count: Int) = change { it.copy(count = count.coerceIn(0, 8)) }

    /** Turns a button on or off in the list being edited. */
    fun toggle(id: String) = change { c ->
        val list = c.active()
        val current = list.firstOrNull { sameFilter(it, id) }
        val next = if (current != null) list - current else list + id
        if (c.auto) {
            val builtIn = AlertFilters.DEFAULT.filter { it != AlertFilters.ALL }
            c.copy(pins = builtIn.filter { it in next } + next.filter { it !in AlertFilters.DEFAULT })
        } else {
            val front = listOf(AlertFilters.ALL, AlertFilters.PEOPLE, AlertFilters.VEHICLES).filter { it in next }
            c.copy(shown = front + next.filter { it !in front })
        }
    }

    /** Adds a label of the owner's own and turns it on; returns what to tell them. */
    fun addLabel(text: String): String {
        val raw = text.trim().lowercase().replace(Regex("\\s+"), " ")
        if (raw.isEmpty()) return ""
        if (!Regex("^[a-z0-9][a-z0-9 _-]{0,29}$").matches(raw)) return "Use letters, numbers, spaces or dashes."
        val k = AlertFilters.normLabel(raw)
        if (config.labels.none { AlertFilters.normLabel(it) == k }) change { it.copy(labels = it.labels + raw) }
        val id = AlertFilters.label(raw)
        if (config.active().none { sameFilter(it, id) }) toggle(id)
        val seen = _state.value.counts?.get(k)
        val name = AlertFilters.name(id)
        return when {
            seen != null -> "Added. “$name” matched ${seen.n} alert${if (seen.n == 1) "" else "s"} in the last 3 weeks."
            _state.value.counts != null -> "Added. Nothing labeled “$raw” in the last 3 weeks yet. It has to match the label your AI model reports."
            else -> "Added."
        }
    }

    fun removeLabel(label: String) {
        val k = AlertFilters.normLabel(label)
        change { it.copy(labels = it.labels.filter { l -> AlertFilters.normLabel(l) != k }) }
    }

    /** Auto keeps its counts fresh in the background. */
    fun refreshIfStale() {
        if (config.auto && nowMs() - config.recentAtMs > STALE_MS) scan()
    }

    /** Forgets what was scanned (signing out). The choices stay. */
    fun clear() {
        job?.cancel()
        scanAtMs = 0L
        _state.update { it.copy(scanning = false, counts = null) }
    }

    /** Looks through the last 3 weeks of alerts, a day at a time, for the labels the cameras report. */
    fun scan() {
        val s = _state.value
        if (s.scanning || (s.counts != null && nowMs() - scanAtMs < SCAN_FRESH_MS)) return
        _state.update { it.copy(scanning = true) }
        job = scope.launch {
            val tally = LabelTally()
            var finished = false
            try {
                val now = nowMs() / 1000
                for (d in 0 until DAYS) {
                    val end = now - d * 86_400L + if (d == 0) 60 else 0
                    val items = try {
                        fetch("index", now - (d + 1) * 86_400L, end)
                    } catch (e: SessionExpiredException) {
                        throw e
                    } catch (e: BlueIrisException) {
                        emptyList()
                    }
                    tally.add(items.filter { shown(it.camera) })
                }
                finished = true
            } catch (e: SessionExpiredException) {
                // The app has already gone back to the sign-in screen.
            } finally {
                if (finished) {
                    scanAtMs = nowMs()
                    change { it.copy(recent = tally.recent(), recentAtMs = nowMs()) }
                    _state.update { it.copy(scanning = false, counts = tally.counts) }
                } else {
                    _state.update { it.copy(scanning = false) }
                }
            }
        }
    }

    companion object {
        const val DAYS = 21
        private const val STALE_MS = 6 * 3_600_000L
        private const val SCAN_FRESH_MS = 600_000L
    }
}
