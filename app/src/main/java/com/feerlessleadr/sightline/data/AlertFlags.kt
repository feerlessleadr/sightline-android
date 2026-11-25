package com.feerlessleadr.sightline.data

/**
 * Blue Iris keeps "flagged" and "protected" as bits in an alert's flags. The `update` command sets the whole flags value,
 * so only the one bit is changed, with 64-bit arithmetic (flags use bits up to 31, for wildlife).
 */
object AlertFlags {
    /** Bit numbers: flagged marks an alert to come back to; protected stops Blue Iris deleting it in its clean-up. */
    const val FLAGGED = 1
    const val PROTECTED = 2

    fun has(flags: Long, bit: Int): Boolean = (flags shr bit) and 1L == 1L

    fun with(flags: Long, bit: Int, on: Boolean): Long = if (on) flags or (1L shl bit) else flags and (1L shl bit).inv()

    /** The record id of an alert from its path (`@123.bvr` gives `123`): plain numbers only; empty if there is none. */
    fun recordId(path: String?): String = path.orEmpty().replace("@", "").replace(Regex("""\..*"""), "").replace(Regex("""[^\w-]"""), "")

    /** What to tell the owner when Blue Iris refuses a change. */
    fun friendlyError(message: String?): String {
        val m = message.orEmpty()
        return if (Regex("access|permission|denied|not allowed|admin", RegexOption.IGNORE_CASE).containsMatchIn(m)) {
            "This Blue Iris user isn't allowed to change alerts."
        } else {
            m.ifEmpty { "Blue Iris didn't accept the change." }
        }
    }
}
