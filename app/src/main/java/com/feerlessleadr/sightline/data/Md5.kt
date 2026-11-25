package com.feerlessleadr.sightline.data

import java.security.MessageDigest

/** Lowercase hex MD5. Blue Iris's login challenge needs it; it is the protocol's choice, not ours. */
fun md5Hex(text: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        for (b in digest) {
            append("0123456789abcdef"[(b.toInt() shr 4) and 0xF])
            append("0123456789abcdef"[b.toInt() and 0xF])
        }
    }
}
