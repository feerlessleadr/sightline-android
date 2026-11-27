package com.feerlessleadr.sightline.data

import okhttp3.HttpUrl

/**
 * The link that opens the web version of Sightline (the page the owner hosts on their Blue Iris server) in the phone's
 * browser. It is the sign-in address plus the page's file name; nothing else goes in it, and never a name or password.
 */
object WebLink {
    const val DEFAULT_PAGE = "sightline.htm"

    private val allowed = Regex("""[A-Za-z0-9][A-Za-z0-9._/-]{0,79}""")

    /** The file name (or a path under the server's address) in a safe form, or null if it is not one. */
    fun clean(page: String?): String? {
        val trimmed = page.orEmpty().trim()
        if (trimmed.startsWith("//")) return null
        val p = trimmed.trimStart('/')
        if (!allowed.matches(p) || p.contains("..") || p.contains("//") || p.endsWith("/")) return null
        return p
    }

    /** The address to open, or null if the file name is not allowed. [base] is the Blue Iris address (HTTPS, ends with a slash). */
    fun url(base: HttpUrl, page: String?): String? {
        val p = clean(page) ?: return null
        return base.newBuilder().addEncodedPathSegments(p).build().toString()
    }
}

/** What "Copy diagnostics" puts on the clipboard: versions, the phone, camera counts and settings. No names, addresses or passwords. */
object Diagnostics {
    fun text(
        appVersion: String,
        blueIrisVersion: String?,
        androidRelease: String,
        sdk: Int,
        device: String,
        cameras: List<Camera>,
        settings: List<Pair<String, String>>,
    ): String {
        val real = cameras.filter { !it.isGroup }
        val groups = cameras.count { it.isGroup }
        val shown = real.count { it.isEnabled && !it.hidden }
        val lines = listOf(
            "Sightline for Android $appVersion · Blue Iris ${blueIrisVersion ?: "not reported"}",
            "Android $androidRelease (API $sdk) · $device",
            "Cameras: ${real.size} in Blue Iris · $shown shown · ${real.count { it.hidden }} hidden · ${real.count { !it.isEnabled }} disabled · ${real.count { it.isEnabled && !it.online }} offline · $groups groups",
            "Settings: " + settings.joinToString(" · ") { "${it.first} ${it.second}" },
        )
        return lines.joinToString("\n")
    }
}
