package com.feerlessleadr.sightline.video

import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTest {
    private fun pictureHeader(): ByteArray {
        val h = ByteArray(40)
        h[0] = 40
        h[4] = 16; h[8] = 16
        "H264".toByteArray().copyInto(h, 16)
        return h
    }

    private fun withSound(tag: Int, channels: Int, rate: Int): ByteArray {
        val sound = ByteArray(18)
        sound[0] = tag.toByte(); sound[1] = (tag shr 8).toByte()
        sound[2] = channels.toByte(); sound[3] = (channels shr 8).toByte()
        for (i in 0..3) sound[4 + i] = (rate shr (8 * i)).toByte()
        return pictureHeader() + sound
    }

    private fun read(header: ByteArray): StreamHeader {
        val b = Buffer()
        b.writeUtf8("blue"); b.writeByte(1); b.writeByte(header.size); b.write(header)
        return BlueStreamReader(b).readHeader()
    }

    @Test
    fun soundFormatFollowsThePictureHeader() {
        val audio = read(withSound(7, 1, 8000)).audio!!
        assertEquals(AudioFormat(7, 1, 8000), audio)
        assertTrue(audio.isMuLawMono)
    }

    @Test
    fun noSoundFormatMeansNoAudio() {
        assertNull(read(pictureHeader()).audio)
    }

    @Test
    fun onlyMuLawMonoIsPlayed() {
        assertFalse(AudioFormat(1, 1, 8000).isMuLawMono) // PCM
        assertFalse(AudioFormat(7, 2, 8000).isMuLawMono) // stereo
        assertFalse(AudioFormat(7, 1, 0).isMuLawMono) // no rate
    }

    @Test
    fun muLawSilenceAndExtremesDecodeLikeTheWebAppTable() {
        assertEquals(0, MuLaw.TABLE[0xFF].toInt()) // silence
        assertEquals(-32124, MuLaw.TABLE[0x00].toInt())
        assertEquals(32124, MuLaw.TABLE[0x80].toInt())
    }

    @Test
    fun gainScalesAndClipsInsteadOfWrapping() {
        val data = byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0x00)
        assertArrayEquals(shortArrayOf(32124, 0, -32124), MuLaw.decode(data, 1f))
        assertArrayEquals(shortArrayOf(32767, 0, -32768), MuLaw.decode(data, 2f))
        assertArrayEquals(shortArrayOf(0, 0, 0), MuLaw.decode(data, 0f))
    }
}
