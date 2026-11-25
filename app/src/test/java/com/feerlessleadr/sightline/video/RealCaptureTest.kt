package com.feerlessleadr.sightline.video

import java.io.EOFException
import java.io.File
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the parser over captures saved by `tools/Sample-BlueIris.ps1` in the owner's temp folder. The captures
 * contain camera pictures, so they are never in the repo; the tests skip themselves when the files are absent.
 */
class RealCaptureTest {
    private val folder = File(System.getProperty("java.io.tmpdir"), "sightline-samples")

    private fun readAll(name: String): Triple<StreamHeader, List<StreamBlock.Video>, Int>? {
        val file = File(folder, name)
        if (!file.exists()) return null
        val reader = BlueStreamReader(Buffer().write(file.readBytes()))
        val header = reader.readHeader()
        val videos = ArrayList<StreamBlock.Video>()
        var others = 0
        try {
            while (true) {
                when (val block = reader.next()) {
                    is StreamBlock.Video -> videos += block
                    StreamBlock.End -> break
                    else -> others++
                }
            }
        } catch (_: EOFException) {
            // The capture stops mid-block.
        }
        return Triple(header, videos, others)
    }

    @Test fun `a real camera capture parses cleanly and starts with a key frame`() {
        val (header, videos, _) = readAll("video-camera.bin").also { assumeTrue(it != null) }!!
        assertTrue(header.width > 0 && header.height > 0)
        assertTrue("expected many frames, got ${videos.size}", videos.size > 20)
        assertTrue(isKeyFrame(videos.first().data, header.isHevc))
    }

    @Test fun `a real group capture parses and its headers give a layout`() {
        val (header, videos, _) = readAll("video-group.bin").also { assumeTrue(it != null) }!!
        assertTrue(videos.size > 20)
        val headers = File(folder, "video-group-headers.txt").readLines()
        val cams = headers.firstOrNull { it.startsWith("X-CamList:", ignoreCase = true) }?.substringAfter(':')?.trim()
        val rects = headers.firstOrNull { it.startsWith("X-Reclist:", ignoreCase = true) }?.substringAfter(':')?.trim()
        val layout = GroupLayout.fromHeaders(cams, rects, header.width, header.height)
        assertNotNull(
            "headers should describe the group layout (cams=${cams?.length} rects=${rects?.take(30)} size=${header.width}x${header.height})",
            layout,
        )
        assertEquals(layout!!.cams.size, layout.rects.size)
    }
}
