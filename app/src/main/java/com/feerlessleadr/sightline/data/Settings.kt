package com.feerlessleadr.sightline.data

/** How long the app may stay away before the fingerprint (or screen lock) is needed again. */
enum class LockAfter(val minutes: Int, val label: String) {
    Always(0, "Always"),
    Minute1(1, "After 1 minute away"),
    Minutes5(5, "After 5 minutes away"),
    Minutes15(15, "After 15 minutes away"),
    Minutes60(60, "After 1 hour away"),

    /** Never asks for the fingerprint: the app signs in by itself with the saved password. */
    Never(Int.MAX_VALUE, "Never (stay signed in)"),
}

/** The video size the player asks for. Auto matches the screen, like the web app. */
enum class VideoQuality(val label: String, val shortSide: Int?, val note: String) {
    Auto("Auto", null, "Matched to this screen"),
    P480("480p", 480, "456 kbps"),
    P720("720p", 720, "1 Mbps"),
    P1080("1080p", 1080, "2 Mbps"),
    P1440("1440p", 1440, "4 Mbps"),
    P2160("4K", 2160, "8 Mbps"),
}

/** Ways the "Test video start" tool opens a live stream, to find out what makes it slow to start. */
enum class ProbeVariant(val label: String) {
    Cookie("As the app does now (cookie, compression allowed)"),
    Identity("No compression"),
    QueryToken("Session in the address (like the web app)"),
    Http11("HTTP/1.1 only"),
    Bare("No size or quality settings"),
    Browser("Browser-like headers"),
}

object LockPolicy {
    /**
     * True if the app can be opened without the fingerprint: it was last in the foreground [lastStopMillis] and
     * the owner allows being away for [lockAfter]. "Always" never skips the prompt and "Never" never asks. A clock
     * set backwards (a time in the future) also counts as locked.
     */
    fun stillUnlocked(lockAfter: LockAfter, lastStopMillis: Long, nowMillis: Long): Boolean {
        if (lockAfter == LockAfter.Never) return true
        if (lockAfter == LockAfter.Always || lastStopMillis <= 0L) return false
        val away = nowMillis - lastStopMillis
        return away in 0..(lockAfter.minutes * 60_000L)
    }
}
