package com.feerlessleadr.sightline.video

import com.feerlessleadr.sightline.data.VideoQuality
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSizingTest {
    @Test fun `group stream follows the grid width and is capped at the 1440p tier`() {
        // A phone: ~1080 px wide grid x 1.25 = 1350 needs the 1920 wide (1080p) tier.
        val phone = groupStreamRequest(1080f, 3840, 2160)
        assertEquals(StreamRequest(1920, 1080, 35, 2048), phone)
        // A huge grid is capped at the 1440p tier.
        assertEquals(StreamRequest(2560, 1440, 40, 4096), groupStreamRequest(5000f, 7680, 4320))
        // Never larger than the group's own picture, and never below the 720p tier's settings.
        assertEquals(StreamRequest(640, 360, 35, 1024), groupStreamRequest(300f, 640, 360))
    }

    @Test fun `camera stream uses the 720p to 1080p tiers`() {
        assertEquals(StreamRequest(1280, 720, 35, 1024), cameraStreamRequest(500f, 281f, 3840, 2160))
        assertEquals(StreamRequest(1920, 1080, 35, 2048), cameraStreamRequest(3000f, 1700f, 3840, 2160))
    }

    @Test fun `player Auto gives a phone 720p, fixed qualities use their tier, portrait boxes turn`() {
        // A 384 x 890 dp phone (pixel ratio 2.6, capped at 2) on a 4K camera: 720p tier.
        assertEquals(StreamRequest(1280, 720, 35, 1024), playerStreamRequest(384f, 890f, 2.6f, 3840, 2160))
        // A 4:3 camera keeps its shape inside the tier's box (this is what the owner's phone asked for).
        assertEquals(StreamRequest(960, 720, 35, 1024), playerStreamRequest(384f, 890f, 2.6f, 2560, 1920))
        // Fixed choices ignore the screen.
        assertEquals(StreamRequest(1920, 1080, 35, 2048), playerStreamRequest(411f, 890f, 2.6f, 3840, 2160, VideoQuality.P1080))
        assertEquals(StreamRequest(3840, 2160, 50, 8192), playerStreamRequest(411f, 890f, 2.6f, 3840, 2160, VideoQuality.P2160))
        // Never larger than the camera.
        assertEquals(StreamRequest(640, 360, 35, 2048), playerStreamRequest(411f, 890f, 2.6f, 640, 360, VideoQuality.P1080))
        // A portrait camera turns the tier's box.
        assertEquals(StreamRequest(720, 1280, 35, 1024), playerStreamRequest(411f, 890f, 2.6f, 2160, 3840, VideoQuality.P720))
    }

    @Test fun `layout from headers maps cameras to fractions, case-insensitively`() {
        val layout = GroupLayout.fromHeaders("\"Front\",\"Back\"", "[0,0,640,360],[640,0,1280,360]", 1280, 360)
        assertNotNull(layout)
        assertArrayEquals(floatArrayOf(0.5f, 0f, 1f, 1f), layout!!.fractionFor("back")!!, 0.0001f)
        assertNull(layout.fractionFor("Garage"))
    }

    @Test fun `inconsistent or missing headers give no layout`() {
        assertNull(GroupLayout.fromHeaders(null, "[0,0,1,1]", 10, 10))
        assertNull(GroupLayout.fromHeaders("\"A\",\"B\"", "[0,0,1,1]", 10, 10))
        assertNull(GroupLayout.fromHeaders("\"A\"", "[0,0,1]", 10, 10))
        assertNull(GroupLayout.fromHeaders("\"A\"", "[0,0,1,1]", 0, 10))
    }

    @Test fun `non-ASCII camera names survive the Latin-1 header round trip`() {
        val name = "Café"
        val asLatin1 = String(("\"" + name + "\"").toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        val layout = GroupLayout.fromHeaders(asLatin1, "[0,0,10,10]", 10, 10)
        assertEquals(listOf(name), layout!!.cams)
    }

    @Test fun `the sequence parameter set is found, so a change of picture size can be seen`() {
        val h264 = byteArrayOf(0, 0, 0, 1, 0x67, 1, 2, 0, 0, 1, 0x68, 5, 0, 0, 1, 0x65, 9, 9)
        assertArrayEquals(byteArrayOf(0x67, 1, 2), sequenceParameterSet(h264, hevc = false))
        val atEnd = byteArrayOf(0, 0, 0, 1, 0x67, 1, 2)
        assertArrayEquals(byteArrayOf(0x67, 1, 2), sequenceParameterSet(atEnd, hevc = false))
        val h265 = byteArrayOf(0, 0, 0, 1, 0x40, 1, 7, 0, 0, 0, 1, 0x42, 1, 5, 6, 0, 0, 1, 0x26, 1)
        assertArrayEquals(byteArrayOf(0x42, 1, 5, 6), sequenceParameterSet(h265, hevc = true))
        assertNull(sequenceParameterSet(byteArrayOf(0, 0, 0, 1, 0x41, 9, 9), hevc = false))
        assertNull(sequenceParameterSet(byteArrayOf(1, 2, 3), hevc = true))
        // Two different sets differ, the same one twice does not.
        val bigger = byteArrayOf(0, 0, 0, 1, 0x67, 1, 3, 0, 0, 1, 0x65, 9)
        assertFalse(sequenceParameterSet(h264, false)!!.contentEquals(sequenceParameterSet(bigger, false)!!))
        assertTrue(sequenceParameterSet(h264, false)!!.contentEquals(sequenceParameterSet(h264.copyOf(), false)!!))
    }

    @Test fun `key frames are found in H264 and H265 access units`() {
        val idr = byteArrayOf(0, 0, 0, 1, 0x67, 1, 2, 0, 0, 1, 0x65, 9, 9) // SPS then IDR slice
        val pFrame = byteArrayOf(0, 0, 0, 1, 0x41, 9, 9)
        assertTrue(isKeyFrame(idr, hevc = false))
        assertFalse(isKeyFrame(pFrame, hevc = false))
        val irap = byteArrayOf(0, 0, 0, 1, 0x26, 1, 0) // type 19 (IDR_W_RADL)
        val trail = byteArrayOf(0, 0, 0, 1, 0x02, 1, 0) // type 1
        assertTrue(isKeyFrame(irap, hevc = true))
        assertFalse(isKeyFrame(trail, hevc = true))
        assertFalse(isKeyFrame(byteArrayOf(1, 2, 3), hevc = false))
    }

    @Test
    fun aTapPicksTheCameraWhoseBoxHoldsIt() {
        val layout = GroupLayout(listOf("A", "B", "C"), listOf(listOf(0, 0, 100, 50), listOf(100, 0, 200, 50), listOf(0, 50, 200, 100)), 200, 100)
        org.junit.Assert.assertEquals("A", layout.cameraAt(0.25f, 0.25f))
        org.junit.Assert.assertEquals("B", layout.cameraAt(0.75f, 0.25f))
        org.junit.Assert.assertEquals("C", layout.cameraAt(0.5f, 0.75f))
        org.junit.Assert.assertNull(layout.cameraAt(1.2f, 0.5f))
    }
}
