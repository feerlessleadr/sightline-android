package com.feerlessleadr.sightline.video

import okio.BufferedSource

/** What the stream header says about the picture. */
/** How the sound in a stream is stored (a WAVEFORMATEX after the picture header): [tag] 7 is mu-law. */
data class AudioFormat(val tag: Int, val channels: Int, val sampleRate: Int) {
    /** The only kind Blue Iris sends and the app plays: 8-bit mu-law, one channel. */
    val isMuLawMono: Boolean get() = tag == 7 && channels == 1 && sampleRate > 0
}

/** What the stream header says about the picture, and the sound if there is any. */
data class StreamHeader(val width: Int, val height: Int, val isHevc: Boolean, val audio: AudioFormat? = null)

sealed interface StreamBlock {
    /** One encoded access unit (Annex-B H.264 or H.265). */
    /** [utcMillis] is when the frame was recorded (or captured, for live video), in milliseconds since 1970; 0 if unknown. */
    class Video(val data: ByteArray, val utcMillis: Long = 0L, val rawMillis: Long = 0L) : StreamBlock

    /** One piece of sound: [data] is the raw samples (mu-law, one byte each). */
    class Audio(val data: ByteArray) : StreamBlock
    data object Status : StreamBlock
    data object End : StreamBlock
}

/** The bytes are not a Blue Iris stream we understand. The message is safe to show. */
class StreamFormatException(message: String) : Exception(message)

/**
 * Reads the Blue Iris live-video container (see docs/blue-iris-api.md): a `blue` header, then `Blue` + type
 * blocks. Reads block until data arrives, so call it off the main thread. Audio and status blocks are skipped.
 * The 18-byte video block header holds the payload length at bytes 14..17, big-endian (confirmed on real
 * captures). A frame is handed over as soon as its own bytes have arrived, never waiting for the next block.
 */
class BlueStreamReader(private val source: BufferedSource) {

    fun readHeader(): StreamHeader {
        val magic = source.readByteArray(4)
        if (!magic.contentEquals(STREAM_MAGIC)) throw StreamFormatException("This is not a Blue Iris video stream.")
        source.skip(1) // version
        val length = source.readByte().toInt() and 0xFF
        if (length < 20) throw StreamFormatException("The video stream header is too short.")
        val header = source.readByteArray(length.toLong())
        val width = littleEndianInt(header, 4)
        val height = littleEndianInt(header, 8)
        if (width !in 1..16384 || height !in 1..16384) throw StreamFormatException("The video stream has an odd picture size.")
        val codec = String(header, 16, 4, Charsets.US_ASCII)
        // After the 40-byte picture header (its own size is in its first four bytes) comes the sound format, if any.
        val pictureSize = littleEndianInt(header, 0)
        val audio = if (pictureSize in 20..length && length - pictureSize >= 14) {
            AudioFormat(
                tag = (header[pictureSize].toInt() and 0xFF) or ((header[pictureSize + 1].toInt() and 0xFF) shl 8),
                channels = (header[pictureSize + 2].toInt() and 0xFF) or ((header[pictureSize + 3].toInt() and 0xFF) shl 8),
                sampleRate = littleEndianInt(header, pictureSize + 4),
            )
        } else {
            null
        }
        return StreamHeader(width, height, isHevc = codec == "H265", audio = audio)
    }

    fun next(): StreamBlock {
        val magic = source.readByteArray(4)
        if (!magic.contentEquals(BLOCK_MAGIC)) throw StreamFormatException("Lost sync in the video stream.")
        return when (val type = source.readByte().toInt()) {
            0 -> readVideo()
            1 -> {
                val size = source.readInt()
                if (size < 0 || size > MAX_BLOCK) throw StreamFormatException("Bad audio block in the video stream.")
                StreamBlock.Audio(source.readByteArray(size.toLong()))
            }
            2 -> {
                val size = (source.readByte().toInt() and 0xFF) - 6
                if (size > 0) source.skip(size.toLong())
                StreamBlock.Status
            }
            4 -> StreamBlock.End
            else -> throw StreamFormatException("Unknown block type $type in the video stream.")
        }
    }

    private fun readVideo(): StreamBlock.Video {
        val head = source.readByteArray(18)
        val length = bigEndianInt(head, 14)
        if (length !in 1..MAX_BLOCK) {
            val hex = head.joinToString(" ") { "%02x".format(it) }
            throw StreamFormatException("Unrecognised video block header: $hex")
        }
        // Bytes 6..13 hold the frame's clock time, little-endian: a 32-bit low part and a 21-bit high part (checked on a real capture).
        val utc = (littleEndianInt(head, 6).toLong() and 0xFFFFFFFFL) + ((littleEndianInt(head, 10).toLong() and 0x1FFFFFL) shl 32)
        // Bytes 2..5 are a millisecond counter (big-endian): for clip files, which may carry no date, time within the file.
        val raw = bigEndianInt(head, 2).toLong() and 0xFFFFFFFFL
        return StreamBlock.Video(source.readByteArray(length.toLong()), utc, raw)
    }

    private fun bigEndianInt(b: ByteArray, at: Int) =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    /** The stream header (unlike the block headers) stores its numbers little-endian. */
    private fun littleEndianInt(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private companion object {
        val STREAM_MAGIC = "blue".toByteArray(Charsets.US_ASCII)
        val BLOCK_MAGIC = "Blue".toByteArray(Charsets.US_ASCII)
        const val MAX_BLOCK = 8 * 1024 * 1024
    }
}
