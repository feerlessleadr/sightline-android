package com.feerlessleadr.sightline.data

import java.net.URI
import java.net.URLDecoder

/**
 * A link that opens this app at a place, for notification buttons (Home Assistant's `clickAction` and `uri`):
 *
 * - `sightline://live/<camera>`: that camera's live view
 * - `sightline://alert/<camera>/<alert id>`: that alert in the player (the id is Blue Iris's `alert_db`, e.g. `@123456.bvr`)
 * - `sightline://alerts`: the Alerts tab
 * - `sightline://live`: the live camera grid
 *
 * A link only chooses a screen. It carries no address or password and never makes the app talk to another host.
 */
sealed interface DeepLink {
    data object Cameras : DeepLink
    data class Live(val camera: String) : DeepLink
    data class Alert(val camera: String, val id: String) : DeepLink
    data object Alerts : DeepLink

    companion object {
        const val SCHEME = "sightline"
        private val safe = Regex("""[\w .@-]{1,80}""")

        /** The link in [text], or null if it is not one of ours or is malformed. */
        fun parse(text: String?): DeepLink? {
            val uri = try { URI(text ?: return null) } catch (e: Exception) { return null }
            if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
            val host = uri.host?.lowercase() ?: uri.authority?.lowercase() ?: return null
            val parts = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.map {
                try { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { return null }
            }
            if (parts.any { !safe.matches(it) }) return null
            return when (host) {
                "alerts" -> if (parts.isEmpty()) Alerts else null
                "live" -> when (parts.size) { 0 -> Cameras; 1 -> Live(parts[0]); else -> null }
                "alert" -> if (parts.size == 2) Alert(parts[0], parts[1]) else null
                else -> null
            }
        }
    }
}
