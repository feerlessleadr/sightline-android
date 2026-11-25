package com.feerlessleadr.sightline.data

/** The kind of thing an alert is about. Each has its own colour in the app, as in the web app. */
enum class AlertClass { Person, Vehicle, Animal, Delivery, Motion }

/** What to show for an alert: its class and the label text, such as "Dog 88%". */
data class AlertTag(val cls: AlertClass, val text: String)

/** An alert from `alertlist`, with only the fields the grid needs. */
data class RecentAlert(val camera: String, val memo: String, val dateSeconds: Long, val flags: Long)

/**
 * Turns Blue Iris's alert text (the "memo", like `person:90%` or `dog:88%,car:55%`) into tags, using the same rules
 * as the web app: camera (ONVIF) event names read as Person, Vehicle and so on, and an alert with no text falls back
 * to Blue Iris's own flag bits (person bit 26, vehicle bit 27, wildlife bit 31).
 */
object AlertTags {
    private val labelNames = mapOf("ups" to "UPS", "usps" to "USPS", "fedex" to "FedEx", "fed ex" to "FedEx", "dhl" to "DHL", "amazon" to "Amazon")
    private val part = Regex("""([a-zA-Z_ ]+?)\s*:?\s*(\d+%)?\s*$""")

    fun of(memo: String?, flags: Long): List<AlertTag> {
        val tags = mutableListOf<AlertTag>()
        val text = memo?.trim().orEmpty()
        if (text.isNotEmpty()) {
            for (piece in text.split(Regex("[,;]+"))) {
                val m = part.find(piece.trim()) ?: continue
                val name = m.groupValues[1].trim()
                if (name.isEmpty() || name.startsWith("nothing", ignoreCase = true)) continue
                val alias = onvifAlias(name)
                if (alias != null) {
                    if (tags.none { it.text == alias.text }) tags += alias
                    if (tags.size == 2) break
                    continue
                }
                val shown = labelNames[name.lowercase()] ?: (name.replaceFirstChar { it.uppercase() })
                val pct = m.groupValues[2]
                tags += AlertTag(classify(name), if (pct.isNotEmpty()) "$shown $pct" else shown)
                if (tags.size == 2) break
            }
        }
        if (tags.isEmpty()) {
            val cls = flagClass(flags)
            tags += AlertTag(cls, when (cls) {
                AlertClass.Person -> "Person"
                AlertClass.Vehicle -> "Vehicle"
                AlertClass.Animal -> "Animal"
                else -> "Motion"
            })
        }
        return tags
    }

    /** Camera-made event names (shortened by Blue Iris), matched by how they start. Null if not a known one. */
    fun onvifAlias(name: String): AlertTag? {
        val k = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        fun pre(vararg p: String) = p.any { k.startsWith(it) }
        return when {
            pre("peopled", "persond", "humand", "bodyd", "pedestrian") || k == "human" || k == "humans" -> AlertTag(AlertClass.Person, "Person")
            pre("faced") -> AlertTag(AlertClass.Person, "Face")
            pre("vehicled", "motorvehicle", "card", "vehicleo") -> AlertTag(AlertClass.Vehicle, "Vehicle")
            pre("animald", "petd", "dogcat") -> AlertTag(AlertClass.Animal, "Animal")
            pre("visitor", "doorbell", "callbutton", "callreq") -> AlertTag(AlertClass.Person, "Visitor")
            pre("objectsin", "fieldd", "intrusion", "regionent") -> AlertTag(AlertClass.Motion, "Intrusion")
            pre("crossed", "lined", "linecross", "tripwire") -> AlertTag(AlertClass.Motion, "Line crossed")
            pre("cellmotion", "motiond", "motiona", "videomotion") || k == "motion" -> AlertTag(AlertClass.Motion, "Motion")
            pre("alarmin", "digitalin") -> AlertTag(AlertClass.Motion, "Alarm input")
            else -> null
        }
    }

    fun classify(label: String): AlertClass {
        val l = label.lowercase()
        return when {
            Regex("""\b(amazon|ups|usps|fed ?ex|dhl|delivery)\b""").containsMatchIn(l) -> AlertClass.Delivery
            Regex("person|people|face").containsMatchIn(l) -> AlertClass.Person
            Regex("car|truck|bus|vehicle|motorcycle|bicycle|boat").containsMatchIn(l) -> AlertClass.Vehicle
            Regex("dog|cat|bear|bird|horse|deer|animal|sheep|cow|fox").containsMatchIn(l) -> AlertClass.Animal
            else -> AlertClass.Motion
        }
    }

    fun flagClass(flags: Long): AlertClass = when {
        hasBit(flags, 26) -> AlertClass.Person
        hasBit(flags, 27) -> AlertClass.Vehicle
        hasBit(flags, 31) -> AlertClass.Animal
        else -> AlertClass.Motion
    }

    private fun hasBit(n: Long, bit: Int) = (n shr bit) and 1L == 1L
}

/** The last AI result seen for one camera and when its alert happened. */
data class CameraTag(val tag: AlertTag, val atMillis: Long)

/**
 * The web app's rule for showing an AI result on a tile: while it is fresh (confirmed in the last 45 s, or the camera
 * is triggered and it was confirmed in the last 3 minutes).
 */
fun CameraTag.isFresh(nowMillis: Long, triggered: Boolean): Boolean {
    val age = nowMillis - atMillis
    return age < 45_000 || (triggered && age < 180_000)
}

/** Merges new alerts into the per-camera latest AI results. Motion-only alerts do not count. Keys are lower case. */
fun mergeTags(previous: Map<String, CameraTag>, alerts: List<RecentAlert>, shown: (String) -> Boolean = { true }): Map<String, CameraTag> {
    var result = previous
    for (it in alerts) {
        if (!shown(it.camera)) continue
        val tag = AlertTags.of(it.memo, it.flags).first()
        if (tag.cls == AlertClass.Motion) continue
        val key = it.camera.lowercase()
        val at = it.dateSeconds * 1000
        val prev = result[key]
        if (prev == null || at > prev.atMillis) result = result + (key to CameraTag(tag, at))
    }
    return result
}
