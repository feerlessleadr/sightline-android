package com.feerlessleadr.sightline.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One of a camera's recording files from Blue Iris's `cliplist`: [path] (like `@123.bvr`) and when it starts and ends. */
data class RecordingFile(val path: String, val startMs: Long, val endMs: Long) {
    /** The file Blue Iris is still writing has no end yet. */
    val growing: Boolean get() = endMs == Long.MAX_VALUE
}

/** Reading Blue Iris's list of recording files, and finding which one holds a moment. */
object RecordingFiles {
    /** A file that was still being written this recently when listed is taken to run on to now. */
    private const val GROWING_MS = 120_000L

    /** Recording files only: alert entries carry a `clip` field and are skipped, as are entries without a start or length. */
    fun parse(data: JsonElement?, fetchedAtMs: Long): List<RecordingFile> {
        val array = data as? JsonArray ?: return emptyList()
        val files = array.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val path = text("path")
            if (path.isEmpty() || text("clip").isNotEmpty() || !path.endsWith(".bvr", ignoreCase = true)) return@mapNotNull null
            val length = text("msec").toDoubleOrNull()?.toLong()?.takeIf { it > 0 } ?: AlertClips.durationMillis(text("filesize"))
            val start = (text("date").toDoubleOrNull() ?: return@mapNotNull null).toLong() * 1000
            if (start <= 0 || length <= 0) return@mapNotNull null
            RecordingFile(path, start, start + length)
        }.sortedBy { it.startMs }
        val last = files.lastOrNull()
        return if (last != null && last.endMs > fetchedAtMs - GROWING_MS) files.dropLast(1) + last.copy(endMs = Long.MAX_VALUE) else files
    }

    /** The file that holds [positionMs], or null when nothing was recorded then. */
    fun find(files: List<RecordingFile>, positionMs: Long): RecordingFile? =
        files.lastOrNull { it.startMs <= positionMs && positionMs < it.endMs }
}

/**
 * A stretch of a camera's recordings chosen for download: [startMs] to [endMs] (milliseconds since 1970). Blue Iris
 * exports from one recording file at a time, and a download may be up to [MAX_MS] long.
 */
data class ClipRange(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs

    /** A new range (or the same one) and what to tell the owner when it was not taken as asked. */
    data class Change(val range: ClipRange, val message: String? = null)

    /** Starts at [positionMs]; the end moves along when it would no longer be after the start, or the clip would be too long. */
    fun withStart(positionMs: Long, latestMs: Long): Change {
        if (positionMs >= latestMs - 1_000L) return Change(this, "Pick an earlier start.")
        var end = endMs
        if (end <= positionMs + 1_000L) end = minOf(positionMs + 10_000L, latestMs)
        if (end - positionMs > MAX_MS) return Change(ClipRange(positionMs, positionMs + MAX_MS), TOO_LONG)
        return Change(ClipRange(positionMs, end))
    }

    /** Ends at [positionMs]; refused when it is not after the start or makes the clip too long. */
    fun withEnd(positionMs: Long): Change = when {
        positionMs <= startMs + 1_000L -> Change(this, "The end needs to be after the start.")
        positionMs - startMs > MAX_MS -> Change(this, TOO_LONG)
        else -> Change(ClipRange(startMs, positionMs))
    }

    /** About how many bytes Blue Iris wrote for this stretch, from the camera's recorded rate in bytes a second; null if unknown. */
    fun approximateBytes(bytesPerSecond: Double?): Long? =
        bytesPerSecond?.takeIf { it > 0 }?.let { (it * lengthMs / 1000.0).toLong() }

    companion object {
        const val MAX_MS = 2 * 3_600_000L
        const val TOO_LONG = "A download can be up to 2 hours."

        /** The minute before [endMs]. */
        fun lastMinute(endMs: Long) = ClipRange(endMs - 60_000L, endMs)

        fun sizeText(bytes: Long?): String = when {
            bytes == null -> ""
            bytes >= 1_000_000_000L -> "%.1f GB".format(java.util.Locale.US, bytes / 1e9)
            bytes >= 1_000_000L -> "${Math.round(bytes / 1e6)} MB"
            else -> ""
        }
    }
}
