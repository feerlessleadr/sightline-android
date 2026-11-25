package com.feerlessleadr.sightline.data

import java.time.Instant
import java.time.ZoneId

/**
 * What to ask Blue Iris for when it prepares an MP4. Re-encoding to H.264 plays smoothly anywhere (quality comes from
 * the encoder [profile] 0 to 3); the original recording is faster but some players stutter. The overlay (timestamp and
 * other text the camera shows) can only be added while re-encoding.
 */
data class ExportOptions(
    val reencode: Boolean = true,
    val profile: Int = 0,
    val audio: Boolean = true,
    val overlay: Boolean = true,
)

/** One export for Blue Iris: [file] is the recording file, and the part of it to cut ([startMs] from its start, [lengthMs] long). */
data class ExportRequest(val file: String, val startMs: Long, val lengthMs: Long, val options: ExportOptions)

/** What Blue Iris answers when it takes an export: where the job is in its queue and where the result will be. */
data class ExportStart(val path: String, val uri: String)

/** One entry of Blue Iris's export queue. [status] is `done`, `error`, `active`, or anything else while it waits. */
data class ExportQueueItem(val path: String, val uri: String, val status: String, val progress: Double)

/** The recording file and range of an alert, and the file name to save an export under (the web app's rules). */
object Exports {
    /** Alerts whose flags carry this bit start part-way into their recording (instead of at its start). */
    private const val STARTS_INSIDE_FILE_BIT = 16

    /** Snapshot-only alerts (a clip starting `@-1.`) have no recording to export. */
    fun canExport(alert: AlertItem): Boolean = alert.clip.isNotEmpty() && !alert.clip.startsWith("@-1.")

    /** When the alert's whole recording range starts and ends (milliseconds since 1970). */
    fun wholeRange(alert: AlertItem): LongRange {
        val duration = AlertClips.durationMillis(alert.fileSize)
        return if (AlertFlags.has(alert.flags, STARTS_INSIDE_FILE_BIT)) {
            alert.timeMs..(alert.timeMs + duration)
        } else {
            (alert.timeMs - alert.offsetMs)..(alert.timeMs + duration)
        }
    }

    /** The export of a whole alert: from where the alert starts to where it ends, cut from its own recording file. */
    fun forAlert(alert: AlertItem, options: ExportOptions): ExportRequest {
        val duration = AlertClips.durationMillis(alert.fileSize)
        val inside = AlertFlags.has(alert.flags, STARTS_INSIDE_FILE_BIT)
        return ExportRequest(
            file = alert.clip,
            startMs = if (inside) alert.offsetMs else 0L,
            lengthMs = if (inside) duration else alert.offsetMs + duration,
            options = options,
        )
    }

    /** Camera name, date and time, like `IPC Kitchen 2026-11-25 1-23-05 PM.mp4`, with characters a file name cannot hold replaced. */
    fun fileName(camera: String, timeMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(timeMs).atZone(zone)
        fun two(n: Int) = n.toString().padStart(2, '0')
        val hour12 = t.hour % 12
        val clean = camera.replace(Regex("""[\\/:*?"<>|]+"""), "-").trim()
        return "$clean ${t.year}-${two(t.monthValue)}-${two(t.dayOfMonth)} ${if (hour12 == 0) 12 else hour12}-${two(t.minute)}-${two(t.second)} ${if (t.hour < 12) "AM" else "PM"}.mp4"
    }

    /** What to tell the owner when Blue Iris refuses an export. */
    fun friendlyError(reason: String?): String {
        val why = reason.orEmpty()
        return if (Regex("denied|permission|admin", RegexOption.IGNORE_CASE).containsMatchIn(why)) {
            "Your Blue Iris user isn't allowed to export clips."
        } else {
            "Blue Iris couldn't export this alert" + (if (why.isNotEmpty()) " ($why)" else "") + "."
        }
    }
}

/** Finds our job in Blue Iris's export queue, and describes the queue when it cannot. */
object ExportQueue {
    /** The last part of a name without its extension or `@`: `Export/@123.mp4` and `123` are the same job. */
    fun idOf(name: String): String =
        name.trim().substringAfterLast('/').substringAfterLast('\\').replace("@", "").substringBefore('.')

    /** A key that tells entries apart, used to spot what is new after an export is started. */
    fun keyOf(item: ExportQueueItem): String = item.path + "|" + item.uri

    /**
     * Our job in [queue]: by the path or the uri Blue Iris gave when it took the export, exactly or by record number; else,
     * if it gave neither (or they match nothing) the one entry that was not in the queue [before] we started, if there
     * is exactly one.
     */
    fun find(queue: List<ExportQueueItem>, started: ExportStart, before: Set<String>): ExportQueueItem? {
        val path = started.path
        val uri = started.uri
        queue.firstOrNull { (path.isNotEmpty() && it.path == path) || (uri.isNotEmpty() && it.uri == uri) }?.let { return it }
        val ids = listOf(path, uri).filter { it.isNotEmpty() }.map { idOf(it) }.filter { it.isNotEmpty() }
        if (ids.isNotEmpty()) {
            queue.firstOrNull { item -> listOf(item.path, item.uri).any { it.isNotEmpty() && idOf(it) in ids } }?.let { return it }
        }
        val fresh = queue.filter { keyOf(it) !in before }
        return fresh.singleOrNull()
    }

    /** What Blue Iris said, for the failure message (record names only, nothing about the footage). */
    fun describe(queue: List<ExportQueueItem>, started: ExportStart): String {
        val items = queue.take(3).joinToString("; ") { "path='${it.path}' uri='${it.uri}' status='${it.status}'" }
        return "Blue Iris answered path='${started.path}' uri='${started.uri}'; its queue then held ${queue.size} item(s)" +
            if (items.isNotEmpty()) ": $items" else "."
    }
}
