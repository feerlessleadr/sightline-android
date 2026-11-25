package com.feerlessleadr.sightline.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface AddressResult {
    data class Ok(val url: HttpUrl) : AddressResult
    data class Bad(val reason: String) : AddressResult
}

/**
 * Turns what the user typed into the Blue Iris base address. HTTPS only: `http://` is refused, and a bare
 * host name gets `https://`. Everything is checked here so the rest of the app only ever sees a safe base URL.
 */
object ServerAddress {
    fun parse(input: String): AddressResult {
        val text = input.trim()
        if (text.isEmpty()) return AddressResult.Bad("Enter your Blue Iris address.")
        val hasHttps = text.startsWith("https://", ignoreCase = true)
        if (!hasHttps && text.contains("://")) {
            // http://, ftp:// or any other scheme.
            return AddressResult.Bad("Only https:// addresses are allowed.")
        }
        val withScheme = if (hasHttps) text else "https://$text"
        val url = withScheme.toHttpUrlOrNull()
            ?: return AddressResult.Bad("That is not a valid address.")
        if (!url.isHttps) return AddressResult.Bad("Only https:// addresses are allowed.")
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            return AddressResult.Bad("Do not put a user name or password in the address.")
        }
        if (url.query != null || url.fragment != null) {
            return AddressResult.Bad("Use just the address, without ? or # parts.")
        }
        // Keep a reverse-proxy sub-path if there is one, and make sure it ends with a slash.
        val path = url.encodedPath
        val base = if (path.endsWith("/")) url else url.newBuilder().encodedPath("$path/").build()
        return AddressResult.Ok(base)
    }
}
