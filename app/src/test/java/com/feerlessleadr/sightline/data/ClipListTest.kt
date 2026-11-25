package com.feerlessleadr.sightline.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipListTest {
    private val now = 1_800_000_000_000L

    private fun parse(json: String, at: Long = now) = RecordingFiles.parse(Json.parseToJsonElement(json), at)

    @Test
    fun recordingFilesAreReadAndAlertEntriesAreSkipped() {
        val files = parse(
            """[
              {"path":"@200.bvr","date":1799990000,"msec":600000},
              {"path":"@100.bvr","date":1799980000,"filesize":"5 min (80MB)"},
              {"path":"@300.bvr","date":1799995000,"msec":1000,"clip":"@300.bvr"},
              {"path":"@400.mp4","date":1799996000,"msec":1000},
              {"path":"@500.bvr","date":0,"msec":1000},
              {"path":"@600.bvr","date":1799997000}
            ]""",
        )
        // @600 has no length at all: like the web app, it is taken to be 10 seconds.
        assertEquals(listOf("@100.bvr", "@200.bvr", "@600.bvr"), files.map { it.path })
        assertEquals(1_799_997_000_000L + 10_000L, files[2].endMs)
        assertEquals(1_799_980_000_000L, files[0].startMs)
        assertEquals(1_799_980_000_000L + 300_000L, files[0].endMs) // length from the size text
        assertEquals(1_799_990_000_000L + 600_000L, files[1].endMs)
    }

    @Test
    fun theNewestFileStillBeingWrittenRunsOnToNow() {
        val recent = (now / 1000 - 30)
        val files = parse("""[{"path":"@1.bvr","date":${recent - 600},"msec":300000},{"path":"@2.bvr","date":$recent,"msec":10000}]""")
        assertFalse(files[0].growing)
        assertTrue(files[1].growing)
        // An old newest file is finished.
        val old = parse("""[{"path":"@1.bvr","date":${now / 1000 - 9000},"msec":300000}]""")
        assertFalse(old[0].growing)
    }

    @Test
    fun aMomentIsFoundInTheFileThatHoldsIt() {
        val files = listOf(RecordingFile("@1.bvr", 1_000, 2_000), RecordingFile("@2.bvr", 2_000, 5_000))
        assertEquals("@1.bvr", RecordingFiles.find(files, 1_500)?.path)
        assertEquals("@2.bvr", RecordingFiles.find(files, 2_000)?.path)
        assertNull(RecordingFiles.find(files, 5_000))
        assertNull(RecordingFiles.find(files, 500))
    }

    @Test
    fun anExportIsCutFromTheFileStartAndShortenedAtTheFileEnd() {
        val file = RecordingFile("@2.bvr", 10_000, 70_000)
        val inside = Exports.forRange(file, ClipRange(20_000, 50_000), ExportOptions())
        assertEquals(10_000L, inside.request.startMs)
        assertEquals(30_000L, inside.request.lengthMs)
        assertFalse(inside.shortened)
        val past = Exports.forRange(file, ClipRange(20_000, 90_000), ExportOptions())
        assertEquals(50_000L, past.request.lengthMs)
        assertEquals(70_000L, past.range.endMs)
        assertTrue(past.shortened)
        val growing = Exports.forRange(RecordingFile("@3.bvr", 10_000, Long.MAX_VALUE), ClipRange(20_000, 90_000), ExportOptions())
        assertFalse(growing.shortened)
        assertEquals(70_000L, growing.request.lengthMs)
    }

    @Test
    fun settingTheStartMovesTheEndAlongWhenNeeded() {
        val r = ClipRange(100_000, 160_000)
        val earlier = r.withStart(80_000, 1_000_000)
        assertEquals(ClipRange(80_000, 160_000), earlier.range)
        val past = r.withStart(200_000, 1_000_000)
        assertEquals(ClipRange(200_000, 210_000), past.range) // end follows: 10 s after the new start
        val nearNow = r.withStart(200_000, 200_500)
        assertEquals(r, nearNow.range)
        assertEquals("Pick an earlier start.", nearNow.message)
    }

    @Test
    fun aDownloadIsLimitedToTwoHours() {
        val r = ClipRange(0, 60_000)
        val tooLong = r.withEnd(ClipRange.MAX_MS + 1)
        assertEquals(r, tooLong.range)
        assertEquals(ClipRange.TOO_LONG, tooLong.message)
        val ok = r.withEnd(ClipRange.MAX_MS)
        assertEquals(ClipRange.MAX_MS, ok.range.endMs)
        assertEquals("The end needs to be after the start.", r.withEnd(500).message)
        // A start far before the end pulls the end in to the limit.
        val far = ClipRange(ClipRange.MAX_MS + 100_000, ClipRange.MAX_MS + 160_000).withStart(0, ClipRange.MAX_MS * 3)
        assertEquals(ClipRange(0, ClipRange.MAX_MS), far.range)
        assertEquals(ClipRange.TOO_LONG, far.message)
    }

    @Test
    fun theSizeIsEstimatedFromTheCamerasRecordedRate() {
        val r = ClipRange(0, 60_000)
        assertEquals(6_000_000L, r.approximateBytes(100_000.0))
        assertNull(r.approximateBytes(null))
        assertNull(r.approximateBytes(0.0))
        assertEquals("", ClipRange.sizeText(null))
        assertEquals("", ClipRange.sizeText(500_000))
        assertEquals("6 MB", ClipRange.sizeText(6_000_000))
        assertEquals("1.5 GB", ClipRange.sizeText(1_500_000_000))
        assertEquals(ClipRange(540_000, 600_000), ClipRange.lastMinute(600_000))
    }

    @Test
    fun theCamerasRateMayComeAsTextOrANumber() {
        fun bps(json: String) = BlueIrisJson.decodeFromString(Camera.serializer(), json).bps
        assertEquals(4096.0, bps("""{"optionValue":"A","BPS":4096}""")!!, 0.0)
        assertEquals(4096.0, bps("""{"optionValue":"A","BPS":"4096"}""")!!, 0.0)
        assertNull(bps("""{"optionValue":"A","BPS":"n/a"}"""))
        assertNull(bps("""{"optionValue":"A"}"""))
    }
}
