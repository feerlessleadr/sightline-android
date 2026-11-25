package com.feerlessleadr.sightline.data

/** Where an alert's recording is and how long it lasts, read the way the web app does. */
object AlertClips {
    /** The recording file an alert plays from: its clip, or the alert record itself when the clip is a snapshot-only placeholder (`@-1.`). */
    fun fileFor(clip: String, path: String): String = if (clip.isNotEmpty() && !clip.startsWith("@-1.")) clip else path

    /** Only the alert's own `.bvr` recording can be read as a file; other files answer "Not BVR". */
    fun isPlayableFile(file: String): Boolean = file.endsWith(".bvr", ignoreCase = true)

    /**
     * How long an alert is, from Blue Iris's size text (`20 sec (391KB)`, `1 min 5 sec`, `2 hr`): the part before the
     * bracket, hours plus minutes plus seconds. 10 seconds if there is nothing to read.
     */
    fun durationMillis(fileSize: String?): Long {
        var text = fileSize.orEmpty()
        val bracket = text.indexOf('(')
        if (bracket > 1) text = text.substring(0, bracket - 1)
        fun part(unit: Char): Long = Regex("""(\d+) ?$unit""").find(text)?.groupValues?.get(1)?.toLong() ?: 0L
        val ms = (part('h') * 3600 + part('m') * 60 + part('s')) * 1000
        return if (ms > 0) ms else 10_000L
    }
}
