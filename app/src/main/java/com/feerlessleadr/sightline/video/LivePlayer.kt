package com.feerlessleadr.sightline.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.VideoStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** The server ended the stream or the connection closed (for a recording: the end of what could be played). */
class StreamEndedException(message: String = "The video stream ended.") : BlueIrisException(message)

/** Counters for the on-screen video diagnostics: frames received, key frames seen, frames drawn. */
data class PlayStats(
    val codec: String,
    val width: Int,
    val height: Int,
    val frames: Int,
    val keyFrames: Int,
    val shown: Int,
    /** Milliseconds from the start of playback to the stream header, the first video frame and the first drawn frame (-1 = not yet). */
    val headerMs: Long = -1,
    val firstFrameMs: Long = -1,
    val firstShownMs: Long = -1,
) {
    override fun toString() =
        "$codec ${width}x$height, got $frames frames ($keyFrames key), drew $shown; header at $headerMs ms, first frame at $firstFrameMs ms, first drawn at $firstShownMs ms (after the connection opened)"
}

/**
 * Decodes a live Blue Iris stream onto [surface] with MediaCodec until the coroutine is cancelled, the stream
 * ends, or something goes wrong (then a [BlueIrisException] with a message safe to show is thrown).
 * Takes ownership of [stream] and always closes it. [onSize] is told the picture size once it is known and
 * [onStats] about once a second. Nothing is shown until the first key frame arrives.
 */
suspend fun playLive(
    stream: VideoStream,
    surface: Surface,
    onSize: (width: Int, height: Int) -> Unit = { _, _ -> },
    onStats: (PlayStats) -> Unit = {},
    /** Told the recording time (milliseconds since 1970) of each frame as it goes to the decoder. */
    onFrameTime: (utcMillis: Long) -> Unit = {},
    /**
     * For recorded video: the playback speed (1 = real time). Frames are then handed to the decoder on the schedule
     * their recording times give, divided by this speed, instead of as soon as they arrive. Null = as they arrive (live).
     */
    speed: Float? = null,
    /**
     * For a recording file (not the timeline): the recording time of the point it was opened at. Files may carry no
     * date, so frame times are then this plus the time since the first frame.
     */
    clipBaseMs: Long? = null,
    /** Plays the stream's sound (if it has any) through this; null = silent. */
    audio: MuLawPlayer? = null,
    /** Told once, after the header, whether the stream carries sound this phone can play. */
    onHasAudio: (Boolean) -> Unit = {},
) {
    withContext(Dispatchers.IO) {
        // Cancelling the coroutine closes the connection, which unblocks the read below.
        val onCancel = coroutineContext.job.invokeOnCompletion { stream.close() }
        var codec: MediaCodec? = null
        try {
            val t0 = SystemClock.elapsedRealtime()
            val reader = BlueStreamReader(stream.source)
            val header = reader.readHeader()
            val headerMs = SystemClock.elapsedRealtime() - t0
            var firstFrameMs = -1L
            var firstShownMs = -1L
            onSize(header.width, header.height)
            val playSound = audio != null && header.audio?.isMuLawMono == true
            onHasAudio(playSound)
            if (playSound) audio!!.start(header.audio!!.sampleRate)
            val mime = if (header.isHevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
            val name = if (header.isHevc) "H265" else "H264"
            val format = MediaFormat.createVideoFormat(mime, header.width, header.height).apply {
                // Key frames of a busy 1080p or 4K picture can be big; the default input buffer can be too small.
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, surface, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var frames = 0
            var keys = 0
            var shown = 0
            var queued = 0L
            var gotKey = false
            var firstUtc = -1L
            var firstRaw = -1L
            var baseNs = System.nanoTime()
            var lastReport = SystemClock.elapsedRealtime()
            while (true) {
                ensureActive()
                when (val block = reader.next()) {
                    is StreamBlock.Video -> {
                        if (firstFrameMs < 0) firstFrameMs = SystemClock.elapsedRealtime() - t0
                        frames++
                        val key = isKeyFrame(block.data, header.isHevc)
                        if (key) keys++
                        if (!gotKey && !key) {
                            // Wait for the first key frame; earlier frames cannot be decoded.
                        } else {
                            gotKey = true
                            var t = block.utcMillis
                            if (t < 1_000_000_000_000L && clipBaseMs != null) {
                                if (firstRaw < 0) firstRaw = block.rawMillis
                                t = clipBaseMs + (block.rawMillis - firstRaw)
                            }
                            if (speed != null && t > 0) {
                                // Play at the recorded pace: hold this frame until its time, so the decoder never piles up.
                                if (firstUtc < 0) firstUtc = t
                                val rel = t - firstUtc
                                var due = baseNs + (rel * 1_000_000L / speed).toLong()
                                val nowNs = System.nanoTime()
                                if (due > nowNs + 2_000_000_000L || due < nowNs - 500_000_000L) {
                                    // Far ahead or well behind (a jump in time, or a slow network): carry on from here.
                                    baseNs = nowNs - (rel * 1_000_000L / speed).toLong()
                                    due = nowNs
                                }
                                val waitMs = (due - nowNs) / 1_000_000L - 15
                                if (waitMs > 0) delay(waitMs)
                            }
                            if (t > 0) onFrameTime(t)
                            val index = decoder.dequeueInputBuffer(INPUT_WAIT_US)
                            // If the decoder is behind, drop this frame rather than building up delay.
                            if (index >= 0) {
                                val buffer = decoder.getInputBuffer(index)
                                if (buffer != null && block.data.size <= buffer.capacity()) {
                                    buffer.clear()
                                    buffer.put(block.data)
                                    decoder.queueInputBuffer(index, 0, block.data.size, queued++ * FRAME_US, 0)
                                } else {
                                    decoder.queueInputBuffer(index, 0, 0, 0, 0)
                                }
                            }
                            while (true) {
                                val out = decoder.dequeueOutputBuffer(info, 0)
                                if (out == MediaCodec.INFO_TRY_AGAIN_LATER) break
                                // A format change needs no action when rendering to a surface.
                                if (out >= 0) {
                                    decoder.releaseOutputBuffer(out, true)
                                    shown++
                                }
                            }
                        }
                        val now = SystemClock.elapsedRealtime()
                        val firstDrawn = firstShownMs < 0 && shown > 0
                        if (firstDrawn) firstShownMs = now - t0
                        // Tell the screen at once when the first picture is drawn, then about once a second.
                        if (firstDrawn || now - lastReport >= 1000) {
                            lastReport = now
                            onStats(PlayStats(name, header.width, header.height, frames, keys, shown, headerMs, firstFrameMs, firstShownMs))
                        }
                    }
                    is StreamBlock.Audio -> if (playSound) audio!!.push(block.data)
                    StreamBlock.End -> throw StreamEndedException()
                    else -> Unit
                }
            }
        } catch (e: StreamFormatException) {
            if (isActive) throw BlueIrisException(e.message ?: "Unexpected video stream.")
        } catch (e: IOException) {
            // Closing the stream on cancel also lands here; only report it if we were not cancelled.
            if (isActive) throw StreamEndedException("The video stream stopped.")
        } catch (e: MediaCodec.CodecException) {
            if (isActive) throw BlueIrisException("This phone could not decode the video (${e.diagnosticInfo.takeLast(60)}).")
        } catch (e: IllegalStateException) {
            if (isActive) throw BlueIrisException("This phone could not decode the video.")
        } finally {
            onCancel.dispose()
            audio?.stop()
            codec?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            stream.close()
        }
    }
}

private const val INPUT_WAIT_US = 20_000L
private const val FRAME_US = 33_333L
private const val MAX_INPUT_BYTES = 4 * 1024 * 1024
