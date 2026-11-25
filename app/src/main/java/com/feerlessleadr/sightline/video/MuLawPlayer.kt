package com.feerlessleadr.sightline.video

import android.media.AudioAttributes
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioTrack
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/** Turns Blue Iris's 8-bit mu-law sound into 16-bit samples. */
object MuLaw {
    /** The standard mu-law table, the same one the web app builds. */
    val TABLE = ShortArray(256) { i ->
        val u = i.inv() and 0xFF
        val e = (u shr 4) and 7
        val m = u and 0x0F
        val v = (((m shl 3) + 0x84) shl e) - 0x84
        (if (u and 0x80 != 0) -v else v).toShort()
    }

    /** Decodes [data], multiplying by [gain] (above 1 makes it louder; samples are clipped, never wrapped). */
    fun decode(data: ByteArray, gain: Float = 1f): ShortArray = ShortArray(data.size) { i ->
        val s = TABLE[data[i].toInt() and 0xFF].toInt()
        if (gain == 1f) s.toShort() else (s * gain).toInt().coerceIn(-32768, 32767).toShort()
    }
}

/**
 * Plays the sound of a stream: samples are queued and written to an [AudioTrack] by a thread of its own, so a slow
 * audio device can never hold up the video. About a quarter of a second of cushion absorbs network jitter; sound
 * that falls more than 1.2 seconds behind is dropped to stay close to real time (as the web app does).
 */
class MuLawPlayer {
    /** 0 mutes, 1 is normal, up to 2 is louder. Takes effect at once for volume up to 1. */
    @Volatile
    var gain: Float = 1f
        set(value) {
            field = value
            track?.let { runCatching { it.setVolume(value.coerceIn(0f, 1f)) } }
        }

    @Volatile private var track: AudioTrack? = null
    @Volatile private var running = false
    @Volatile private var rate = 0
    private var thread: Thread? = null
    private val queue = LinkedBlockingQueue<ShortArray>()
    private val queued = AtomicInteger(0)

    fun start(sampleRate: Int) {
        stop()
        rate = sampleRate
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AndroidAudioFormat.CHANNEL_OUT_MONO, AndroidAudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            )
            .setAudioFormat(
                AndroidAudioFormat.Builder()
                    .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AndroidAudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBytes, sampleRate * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack.setVolume(gain.coerceIn(0f, 1f))
        track = audioTrack
        running = true
        audioTrack.play()
        thread = Thread {
            val cushion = ShortArray(sampleRate / 4)
            runCatching { audioTrack.write(cushion, 0, cushion.size) }
            while (running) {
                val chunk = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                queued.addAndGet(-chunk.size)
                if (runCatching { audioTrack.write(chunk, 0, chunk.size) }.getOrDefault(-1) < 0 && !running) break
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /** Queues a piece of mu-law sound. Dropped if sound is not playing or too much is already waiting. */
    fun push(ulaw: ByteArray) {
        if (!running) return
        if (queued.get() > rate * 12 / 10) return
        val pcm = MuLaw.decode(ulaw, max(1f, gain)) // up to 1 is the track's own volume, above 1 is applied to the samples
        queued.addAndGet(pcm.size)
        queue.offer(pcm)
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
        queue.clear()
        queued.set(0)
    }
}
