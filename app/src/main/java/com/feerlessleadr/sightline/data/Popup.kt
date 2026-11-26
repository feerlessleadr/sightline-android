package com.feerlessleadr.sightline.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Which detections make a camera pop up on the camera grid: any of the kinds `person`, `vehicle`, `animal`, and any labels
 * of the owner's own (lower case). Nothing chosen means off. Plain motion never pops up.
 */
@Serializable
data class PopupConfig(val kinds: List<String> = emptyList(), val labels: List<String> = emptyList(), val enabled: Boolean = true) {
    /** What was chosen to pop up. */
    val hasChoices: Boolean get() = kinds.isNotEmpty() || labels.isNotEmpty()

    /** Pop-ups happen: switched on, with something chosen. */
    val isOn: Boolean get() = enabled && hasChoices

    fun withKind(kind: String, on: Boolean) = copy(kinds = Popup.KINDS.map { it.first }.filter { if (it == kind) on else it in kinds })

    fun withLabel(label: String, on: Boolean): PopupConfig {
        val k = AlertFilters.normLabel(label)
        val rest = labels.filter { AlertFilters.normLabel(it) != k }
        return copy(labels = if (on) rest + label else rest)
    }

    fun hasLabel(label: String) = labels.any { AlertFilters.normLabel(it) == AlertFilters.normLabel(label) }

    /** A short description for the settings row. */
    fun summary(): String {
        val names = (Popup.KINDS.filter { it.first in kinds }.map { it.second } + labels.map { AlertFilters.name(AlertFilters.label(it)) }).joinToString(", ")
        return when {
            !hasChoices -> "Off"
            !enabled -> "Off ($names)"
            else -> names
        }
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(text: String?): PopupConfig =
            if (text.isNullOrBlank()) PopupConfig() else try { json.decodeFromString(serializer(), text) } catch (e: Exception) { PopupConfig() }
    }
}

/** A camera to pop up, why, and when its alert happened. */
data class PopupEvent(val camera: String, val tag: AlertTag, val atMs: Long)

object Popup {
    val KINDS = listOf("person" to "People", "vehicle" to "Vehicles", "animal" to "Animals")

    private fun kindOf(cls: AlertClass) = when (cls) {
        AlertClass.Person -> "person"
        AlertClass.Vehicle -> "vehicle"
        AlertClass.Animal -> "animal"
        else -> null
    }

    /** What to show for an alert that should pop up (People, Vehicles or Animals by type, or one of the labels), or null. */
    fun wants(alert: RecentAlert, config: PopupConfig): AlertTag? {
        val tags = AlertTags.of(alert.memo, alert.flags)
        val first = tags.first()
        if (kindOf(first.cls)?.let { it in config.kinds } == true) return first
        if (config.labels.isEmpty()) return null
        val keys = AlertFilters.memoKeys(alert.memo)
        val hit = config.labels.firstOrNull { AlertFilters.normLabel(it) in keys } ?: return null
        val k = AlertFilters.normLabel(hit)
        return tags.firstOrNull { AlertFilters.normLabel(it.text.replace(Regex("""\s*\d+%$"""), "")) == k }
            ?: AlertTag(AlertTags.classify(hit), AlertFilters.noun(AlertFilters.label(hit)).replaceFirstChar { it.uppercase() })
    }

    /** Adds a label typed in by the owner; returns the new choice and what to tell them. [seen] is how many alerts carried it lately, if known. */
    fun addLabel(config: PopupConfig, text: String, seen: Int?, scanned: Boolean): Pair<PopupConfig, String> {
        val raw = text.trim().lowercase().replace(Regex("\\s+"), " ")
        if (raw.isEmpty()) return config to ""
        if (!Regex("^[a-z0-9][a-z0-9 _-]{0,29}$").matches(raw)) return config to "Use letters, numbers, spaces or dashes."
        val name = AlertFilters.name(AlertFilters.label(raw))
        val note = when {
            seen != null && seen > 0 -> "Added. “$name” matched $seen alert${if (seen == 1) "" else "s"} in the last 3 weeks."
            scanned -> "Added. Nothing labeled “$raw” in the last 3 weeks yet. It has to match the label your AI model reports."
            else -> "Added."
        }
        return config.withLabel(raw, true) to note
    }
}

/**
 * Decides which new alerts pop a camera up. Only what is happening now (an alert under 30 s old), once per alert, only for
 * cameras on the grid, and an older alert never replaces a newer one that is showing.
 */
class PopupTracker(private val nowMs: () -> Long = System::currentTimeMillis) {
    private val seen = LinkedHashSet<String>()
    private var currentMs = 0L

    /** The alert to pop up from [alerts] (newest first), or null. [showing] is whether a pop-up is on screen now. */
    fun consider(alerts: List<RecentAlert>, config: PopupConfig, onGrid: (String) -> Boolean, showing: Boolean): PopupEvent? {
        if (!config.isOn) return null
        var result: PopupEvent? = null
        var on = showing
        for (alert in alerts) {
            val key = alert.path.ifEmpty { alert.camera + "|" + alert.dateSeconds }
            if (!seen.add(key)) continue
            if (seen.size > 500) seen.remove(seen.first())
            val at = alert.dateSeconds * 1000
            val tag = Popup.wants(alert, config) ?: continue
            if (nowMs() - at > 30_000) continue
            if (!onGrid(alert.camera)) continue
            if (on && at < currentMs) continue
            currentMs = at
            on = true
            if (result == null) result = PopupEvent(alert.camera, tag, at)
        }
        return result
    }
}
