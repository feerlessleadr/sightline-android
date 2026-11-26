package com.feerlessleadr.sightline.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The filter buttons of the Alerts tab, by id: `all`, `people`, `vehicles`, `animals`, `flagged`, or `label:<text>` for
 * an AI label. Same rules as the web app: People, Vehicles and Animals use Blue Iris's AI flag bits or the alert's label
 * (its memo, like `dog:94%`); labels ignore capitalisation and plurals.
 */
object AlertFilters {
    const val ALL = "all"
    const val PEOPLE = "people"
    const val VEHICLES = "vehicles"
    const val ANIMALS = "animals"
    const val FLAGGED = "flagged"

    /** The buttons shown by default, in order. */
    val DEFAULT = listOf(ALL, PEOPLE, VEHICLES, ANIMALS, FLAGGED)

    private const val PERSON_BIT = 26
    private const val VEHICLE_BIT = 27
    private const val WILDLIFE_BIT = 31

    private val vehicles = Regex("""\b(car|truck|bus|motorcycle|bicycle|boat|vehicle|van|suv)s?\b""")
    private val animals = Regex(
        """\b(dog|cat|bear|bird|deer|horse|sheep|cow|fox|coyote|raccoon|squirrel|rabbit|skunk|possum|opossum|animal|elephant|zebra|giraffe)s?\b""",
    )
    private val people = Regex("""\b(person|people)\b""")

    /** True if [word] (already normalised) is a vehicle word like car or trucks. */
    fun vehicleWord(word: String) = vehicles.containsMatchIn(word)

    fun label(text: String) ="label:" + text.trim().lowercase()

    /** The text on the button. */
    fun name(id: String): String = when (id) {
        ALL -> "All"
        PEOPLE -> "People"
        VEHICLES -> "Vehicles"
        ANIMALS -> "Animals"
        FLAGGED -> "Flagged"
        else -> if (id.startsWith("label:")) plural(id.removePrefix("label:")) else id
    }

    /** What the filter finds, for messages like "No dog alerts in the last 7 days". */
    fun noun(id: String): String = when (id) {
        PEOPLE -> "person"
        VEHICLES -> "vehicle"
        ANIMALS -> "animal"
        FLAGGED -> "flagged"
        else -> if (id.startsWith("label:")) id.removePrefix("label:").let { brands[it] ?: it } else ""
    }

    /** "dog and Dogs and DOGS" are one label: lower case, no spaces or dashes, singular. */
    fun normLabel(text: String): String {
        var k = text.lowercase().replace(Regex("[^a-z0-9]+"), "")
        if (k.length > 4 && Regex("(ches|shes|xes|sses)$").containsMatchIn(k)) {
            k = k.dropLast(2)
        } else if (k.length > 3 && Regex("[^aeiou]ies$").containsMatchIn(k)) {
            k = k.dropLast(3) + "y"
        } else if (k.length > 2 && Regex("[^s]s$").containsMatchIn(k)) {
            k = k.dropLast(1)
        }
        return k
    }

    /** The AI labels of one alert as lower-case names: `person:91%,car:55%` gives `person`, `car`. */
    fun memoNames(memo: String?): List<String> =
        memo.orEmpty().lowercase().split(Regex("[,;]+")).map { piece ->
            piece.replace(Regex(""":?\s*\d+(\.\d+)?\s*%?"""), "").replace(Regex("[^a-z0-9 _-]+"), " ").trim()
        }.filter { p -> p.any { it in 'a'..'z' } }

    /** The label keys of an alert: camera (ONVIF) event names read as person, vehicle and so on; others normalised. */
    fun memoKeys(memo: String?): Set<String> {
        val keys = HashSet<String>()
        for (n in memoNames(memo)) {
            val alias = AlertTags.onvifAlias(n)
            if (alias != null) {
                keys += alias.text.lowercase().replace(" ", "")
                continue
            }
            keys += normLabel(n)
            n.split(Regex("[\\s_-]+")).filter { it.isNotEmpty() }.forEach { keys += normLabel(it) }
        }
        return keys
    }

    /** True if [alert] belongs under filter [id]; [flags] is its flags value including any change made on this phone. */
    fun matches(id: String, alert: AlertItem, flags: Long = alert.flags): Boolean {
        val memo = alert.memo.lowercase()
        return when {
            id == ALL -> true
            id == PEOPLE -> AlertFlags.has(alert.flags, PERSON_BIT) || people.containsMatchIn(memo) || memoKeys(alert.memo).let { "person" in it || "face" in it }
            id == VEHICLES -> AlertFlags.has(alert.flags, VEHICLE_BIT) || vehicles.containsMatchIn(memo) || "vehicle" in memoKeys(alert.memo)
            id == ANIMALS -> animals.containsMatchIn(memo) || AlertFlags.has(alert.flags, WILDLIFE_BIT) || "animal" in memoKeys(alert.memo)
            id == FLAGGED -> AlertFlags.has(flags, AlertFlags.FLAGGED)
            id.startsWith("label:") -> normLabel(id.removePrefix("label:")) in memoKeys(alert.memo)
            else -> true
        }
    }

    /** True if [alert] matches any of [ids] (none selected means all). */
    fun matchesAny(ids: List<String>, alert: AlertItem, flags: Long = alert.flags): Boolean =
        ids.isEmpty() || ids.any { matches(it, alert, flags) }

    /** What the selection is called in messages: "dog or person alerts". */
    fun selectionNoun(ids: List<String>): String {
        val nouns = ids.map { noun(it) }.filter { it.isNotEmpty() }
        return if (nouns.isEmpty()) "alerts" else nouns.joinToString(" or ") + " alerts"
    }

    private val brands = mapOf("ups" to "UPS", "usps" to "USPS", "fedex" to "FedEx", "fed ex" to "FedEx", "dhl" to "DHL", "amazon" to "Amazon")
    private val irregular = mapOf("person" to "People", "deer" to "Deer", "sheep" to "Sheep", "fish" to "Fish", "mouse" to "Mice", "goose" to "Geese")

    private fun properName(label: String): String =
        brands[label] ?: label.replace(Regex("""\b[a-z]""")) { it.value.uppercase() }

    private fun plural(label: String): String {
        brands[label]?.let { return it }
        irregular[label]?.let { return it }
        val t = properName(label)
        return when {
            label.endsWith("s", ignoreCase = true) || label.any { it.isWhitespace() } -> t
            Regex("(ch|sh|x)$", RegexOption.IGNORE_CASE).containsMatchIn(label) -> t + "es"
            Regex("[^aeiou]y$", RegexOption.IGNORE_CASE).containsMatchIn(label) -> t.dropLast(1) + "ies"
            else -> t + "s"
        }
    }
}

/** A stretch of time to look for alerts in, in seconds since 1970. */
data class AlertRange(val startSec: Long, val endSec: Long) {
    companion object {
        fun lastHour(nowSec: Long) = AlertRange(nowSec - 3600, nowSec + 60)

        fun day(date: LocalDate, zone: ZoneId): AlertRange {
            val start = date.atStartOfDay(zone).toEpochSecond()
            return AlertRange(start, start + 86_399)
        }

        fun today(nowSec: Long, zone: ZoneId) = day(Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate(), zone)

        fun yesterday(nowSec: Long, zone: ZoneId) = day(Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate().minusDays(1), zone)

        /** 8 PM yesterday to 6 AM today. */
        fun lastNight(nowSec: Long, zone: ZoneId): AlertRange {
            val midnight = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()
            return AlertRange(midnight - 4 * 3600, midnight + 6 * 3600)
        }
    }
}
