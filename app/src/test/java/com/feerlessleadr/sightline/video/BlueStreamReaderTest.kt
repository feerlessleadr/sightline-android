package com.feerlessleadr.sightline.video

import okio.Buffer
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The bytes here are invented: a 40-byte header and tiny fake payloads, nothing from a real camera. */
class BlueStreamReaderTest {
    private fun header(width: Int = 1920, height: Int = 1080, codec: String = "H264"): ByteArray {
        val h = ByteArray(40)
        h[0] = 40
        fun le(at: Int, v: Int) { for (i in 0..3) h[at + i] = (v shr (8 * i)).toByte() }
        le(4, width)
        le(8, height)
        codec.toByteArray().copyInto(h, 16)
        return h
    }

    private fun Buffer.writeStreamHeader(h: ByteArray = header()) = apply {
        writeUtf8("blue"); writeByte(1); writeByte(h.size); write(h)
    }

    private fun Buffer.writeVideo(payload: ByteArray, bigEndian: Boolean) = apply {
        writeUtf8("Blue"); writeByte(0)
        val head = ByteArray(18)
        for (i in 0..3) {
            head[14 + i] = if (bigEndian) (payload.size shr (8 * (3 - i))).toByte() else (payload.size shr (8 * i)).toByte()
        }
        write(head); write(payload)
    }

    private val annexB = byteArrayOf(0, 0, 0, 1, 0x65, 1, 2, 3)

    @Test fun `header gives size and codec`() {
        val reader = BlueStreamReader(Buffer().writeStreamHeader())
        assertEquals(StreamHeader(1920, 1080, isHevc = false), reader.readHeader())
        val hevc = BlueStreamReader(Buffer().writeStreamHeader(header(1280, 720, "H265")))
        assertEquals(StreamHeader(1280, 720, isHevc = true), hevc.readHeader())
    }

    @Test fun `big-endian video blocks are read, audio samples kept, status skipped, end recognised`() {
        val b = Buffer().writeStreamHeader()
        b.writeVideo(annexB, bigEndian = true)
        b.writeUtf8("Blue"); b.writeByte(1); b.writeInt(3); b.write(byteArrayOf(9, 9, 9))
        b.writeUtf8("Blue"); b.writeByte(2); b.writeByte(6 + 2); b.write(byteArrayOf(7, 7))
        b.writeVideo(annexB, bigEndian = true)
        b.writeUtf8("Blue"); b.writeByte(4)
        val reader = BlueStreamReader(b)
        reader.readHeader()
        assertArrayEquals(annexB, (reader.next() as StreamBlock.Video).data)
        assertArrayEquals(byteArrayOf(9, 9, 9), (reader.next() as StreamBlock.Audio).data)
        assertEquals(StreamBlock.Status, reader.next())
        assertArrayEquals(annexB, (reader.next() as StreamBlock.Video).data)
        assertEquals(StreamBlock.End, reader.next())
    }

    @Test fun `a frame is handed over without waiting for the next block to arrive`() {
        val all = Buffer().writeStreamHeader()
        all.writeVideo(ByteArray(300) { 5 }, bigEndian = true)
        // A source that holds exactly one frame and then fails if anything tries to read more, the way a live
        // stream would simply block until the next frame is produced.
        val served = all.readByteArray()
        var given = false
        val raw = object : okio.Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                check(!given) { "tried to read beyond the first frame" }
                given = true
                sink.write(served)
                return served.size.toLong()
            }

            override fun timeout() = okio.Timeout.NONE

            override fun close() = Unit
        }
        val reader = BlueStreamReader(raw.buffer())
        reader.readHeader()
        assertEquals(300, (reader.next() as StreamBlock.Video).data.size)
    }

    @Test fun `a wrong block length is reported with the header bytes`() {
        val b = Buffer().writeStreamHeader().writeUtf8("Blue").writeByte(0)
        b.write(ByteArray(14)).writeInt(0).writeUtf8("pad")
        val reader = BlueStreamReader(b).also { it.readHeader() }
        try {
            reader.next()
            fail()
        } catch (e: StreamFormatException) {
            assertTrue(e.message!!.startsWith("Unrecognised video block header"))
        }
    }

    @Test fun `bad magic, odd sizes and unknown blocks are refused`() {
        try {
            BlueStreamReader(Buffer().writeUtf8("nope....")).readHeader()
            fail()
        } catch (_: StreamFormatException) {
        }
        try {
            BlueStreamReader(Buffer().writeStreamHeader(header(0, 0))).readHeader()
            fail()
        } catch (_: StreamFormatException) {
        }
        val lost = Buffer().writeStreamHeader().writeUtf8("Xxxx").writeByte(0)
        val r1 = BlueStreamReader(lost).also { it.readHeader() }
        try { r1.next(); fail() } catch (e: StreamFormatException) { assertTrue(e.message!!.contains("sync")) }
        val unknown = Buffer().writeStreamHeader().writeUtf8("Blue").writeByte(9)
        val r2 = BlueStreamReader(unknown).also { it.readHeader() }
        try { r2.next(); fail() } catch (e: StreamFormatException) { assertTrue(e.message!!.contains("Unknown block")) }
    }

    @Test fun `an impossible block length is refused and shows the header bytes`() {
        val b = Buffer().writeStreamHeader().writeUtf8("Blue").writeByte(0).write(ByteArray(18) { 0xFF.toByte() })
        val reader = BlueStreamReader(b).also { it.readHeader() }
        try {
            reader.next()
            fail()
        } catch (e: StreamFormatException) {
            assertTrue(e.message!!.startsWith("Unrecognised video block header: ff ff"))
        }
    }

    @Test fun `a stream that ends mid-block is an end-of-file error`() {
        val b = Buffer().writeStreamHeader().writeUtf8("Blue").writeByte(0).write(ByteArray(5))
        val reader = BlueStreamReader(b).also { it.readHeader() }
        try {
            reader.next()
            fail()
        } catch (_: java.io.EOFException) {
            assertFalse(false)
        }
    }
}
