package com.feerlessleadr.sightline.data

import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One disk Blue Iris records to, in MiB: what it may use ([allocated]), what it uses, and the disk's free and total space. */
data class DiskInfo(val name: String, val allocated: Long, val used: Long, val free: Long, val total: Long) {
    /** The disk drawn as in Blue Iris's own UI: recordings, allocated but free, other files and free space. */
    fun segments(): DiskSegments {
        var cap = total
        val monitored = cap > 0
        if (monitored && cap - used - free < 0) cap = used + free
        val over = maxOf(0L, used - allocated)
        val recordings = used - over
        val allocatedFree = maxOf(0L, minOf(allocated - used, free))
        val other = if (monitored) maxOf(0L, cap - free - used) else 0L
        val freeOutside = if (monitored) maxOf(0L, free - allocatedFree) else 0L
        val whole = if (monitored) cap else (maxOf(allocated, used).takeIf { it > 0 } ?: 1L)
        val note = when {
            over > 0 -> "Blue Iris is keeping more recordings than this disk’s allocation."
            monitored && allocated - used > free -> "There isn’t enough free space left for Blue Iris to fill its allocation."
            else -> ""
        }
        return DiskSegments(recordings, over, allocatedFree, other, freeOutside, monitored, cap, whole, note)
    }
}

class DiskSegments(
    val recordings: Long,
    val overAllocation: Long,
    val allocatedFree: Long,
    val other: Long,
    val free: Long,
    val monitored: Boolean,
    val capacity: Long,
    val whole: Long,
    val note: String,
)

/** Blue Iris's `status` answer, with the fields the Server status screen shows. */
data class ServerStatus(
    val cpu: Double?,
    val gpu: Double?,
    val memory: String?,
    val physicalMemory: String?,
    val memoryLoad: Double?,
    val connections: Int?,
    val uptime: String?,
    val warnings: Int,
    val profile: String?,
    val schedule: String?,
    val locked: Boolean,
    /** `red`, `green` or `yellow` (the status light), if reported. */
    val signal: String?,
    val clips: String?,
    val disks: List<DiskInfo>,
) {
    companion object {
        fun parse(data: JsonElement?): ServerStatus {
            val d = data as? JsonObject ?: JsonObject(emptyMap())
            fun text(key: String) = (d[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
            fun number(key: String) = text(key)?.trim()?.removeSuffix("%")?.toDoubleOrNull()
            val disks = (d["disks"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                fun long(key: String) = o[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
                DiskInfo(o["disk"]?.jsonPrimitive?.contentOrNull ?: "Disk", long("allocated"), long("used"), long("free"), long("total"))
            }
            return ServerStatus(
                cpu = number("cpu"),
                gpu = number("gpu"),
                memory = text("mem"),
                physicalMemory = text("memphys"),
                memoryLoad = number("memload"),
                connections = number("cxns")?.toInt(),
                uptime = text("uptime"),
                warnings = number("warnings")?.toInt() ?: 0,
                profile = text("profile"),
                schedule = text("schedule"),
                locked = text("lock") == "1",
                signal = when (text("signal")) { "0" -> "red"; "1" -> "green"; "2" -> "yellow"; else -> null },
                clips = text("clips"),
                disks = disks,
            )
        }
    }
}

/** One camera row of the status screen: its frame rate and bit rate as Blue Iris reports them. */
data class CameraRate(val name: String, val fps: Double, val mbps: Double, val state: String)

/** The numbers on the Server status screen, worked out the way the web app does. */
object ServerNumbers {
    /** Megabytes (as Blue Iris counts disk space) as "7.2 TB", "460 GB" or "512 MB". */
    fun mib(m: Long): String {
        val g = m / 1024.0
        val t = g / 1024.0
        return when {
            t >= 1 -> String.format(Locale.ROOT, "%.${if (t >= 10) 1 else 2}f TB", t)
            g >= 1 -> String.format(Locale.ROOT, "%.${if (g >= 100) 0 else 1}f GB", g)
            else -> "$m MB"
        }
    }

    /** Blue Iris's own size text ("2.29GB") shortened to "2.3 GB". */
    fun biSize(text: String?): String {
        val m = Regex("""^([\d.]+)\s*([KMGT])""", RegexOption.IGNORE_CASE).find(text.orEmpty()) ?: return text.orEmpty()
        val n = m.groupValues[1].toDoubleOrNull() ?: return text.orEmpty()
        return String.format(Locale.ROOT, "%.${if (n >= 100) 0 else 1}f %sB", n, m.groupValues[2].uppercase())
    }

    /** Uptime as `days:hours:minutes:seconds` to "4d 20h", "20h 40m" or "40m"; also how many milliseconds it is. */
    fun uptime(text: String?): Pair<String, Long> {
        val p = text.orEmpty().split(":").map { it.trim().toLongOrNull() }
        if (p.size != 4 || p.any { it == null }) return (text?.ifEmpty { null } ?: "?") to 0L
        val (d, h, m, s) = p.map { it!! }
        val ms = (((d * 24 + h) * 60 + m) * 60 + s) * 1000
        val short = when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
        return short to ms
    }

    /** The cameras Blue Iris is really running (not groups or hidden ones), their rates, and the totals. */
    fun rates(cameras: List<Camera>, showHidden: Boolean = false): List<CameraRate> =
        cameras.filter { !it.isGroup && !it.optionValue.startsWith("@") && !it.optionValue.startsWith("+") && (showHidden || !it.hidden) }
            .sortedBy { it.displayName.lowercase() }
            .map {
                val state = when {
                    !it.isEnabled -> "disabled"
                    !it.online -> "no signal"
                    else -> ""
                }
                CameraRate(it.displayName, it.fps ?: 0.0, (it.bps ?: 0.0) * 8 / 1e6, state)
            }
}
