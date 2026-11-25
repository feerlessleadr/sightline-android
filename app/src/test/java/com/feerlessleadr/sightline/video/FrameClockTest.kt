package com.feerlessleadr.sightline.video

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameClockTest {
    /** A frame's clock time is stored little-endian in bytes 6..13 of the 18-byte block header (checked on a real capture). */
    @Test fun videoBlockCarriesItsRecordingTime() {
        val utc = 1_795_582_105_214L
        val b = Buffer()
        b.write("blue".toByteArray()).writeByte(1).writeByte(40)
        val header = ByteArray(40)
        header[4] = 16; header[8] = 16
        "H264".toByteArray().copyInto(header, 16)
        b.write(header)
        b.write("Blue".toByteArray()).writeByte(0)
        val head = ByteArray(18)
        for (i in 0 until 4) head[6 + i] = ((utc and 0xFFFFFFFFL) shr (8 * i)).toByte()
        for (i in 0 until 4) head[10 + i] = ((utc shr 32) shr (8 * i)).toByte()
        head[14] = 0; head[15] = 0; head[16] = 0; head[17] = 3 // payload length 3, big-endian
        b.write(head).write(byteArrayOf(1, 2, 3))
        val reader = BlueStreamReader(b)
        reader.readHeader()
        val video = reader.next() as StreamBlock.Video
        assertEquals(utc, video.utcMillis)
        assertEquals(3, video.data.size)
    }
}
