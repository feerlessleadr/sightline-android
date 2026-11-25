package com.feerlessleadr.sightline.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private fun alert(flags: Long = 0, offset: Long = 5_000, size: String = "20 sec (391KB)", clip: String = "@100.bvr") =
        AlertItem("Cam1", "", 1_800_000_100_000L, flags, "@7.bvr", clip, offset, size)

    @Test
    fun anAlertThatStartsAtTheStartOfItsFileIsCutFromTheBeginningToItsEnd() {
        val r = Exports.forAlert(alert(), ExportOptions())
        assertEquals("@100.bvr", r.file)
        assertEquals(0L, r.startMs)
        assertEquals(25_000L, r.lengthMs) // 5 s lead-in plus the alert's 20 s
    }

    @Test
    fun anAlertThatStartsInsideItsFileIsCutFromWhereItStarts() {
        val r = Exports.forAlert(alert(flags = 1L shl 16), ExportOptions())
        assertEquals(5_000L, r.startMs)
        assertEquals(20_000L, r.lengthMs)
    }

    @Test
    fun theWholeRangeMatchesWhatIsExported() {
        val a = alert()
        assertEquals((a.timeMs - 5_000)..(a.timeMs + 20_000), Exports.wholeRange(a))
        val inside = alert(flags = 1L shl 16)
        assertEquals(inside.timeMs..(inside.timeMs + 20_000), Exports.wholeRange(inside))
    }

    @Test
    fun snapshotOnlyAlertsCannotBeExported() {
        assertTrue(Exports.canExport(alert()))
        assertFalse(Exports.canExport(alert(clip = "@-1.bvr")))
        assertFalse(Exports.canExport(alert(clip = "")))
    }

    @Test
    fun theFileNameHasTheCameraDateAndTime() {
        val zone = ZoneId.of("America/New_York")
        val t = LocalDateTime.of(2026, 11, 25, 13, 23, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("IPC Kitchen 2026-11-25 1-23-05 PM.mp4", Exports.fileName("IPC Kitchen", t, zone))
        val midnight = LocalDateTime.of(2026, 11, 25, 0, 7, 9).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Front-Door 2026-11-25 12-07-09 AM.mp4", Exports.fileName("Front/Door", midnight, zone))
    }

    @Test
    fun refusalsAreExplained() {
        assertEquals("Your Blue Iris user isn't allowed to export clips.", Exports.friendlyError("Access denied"))
        assertEquals("Blue Iris couldn't export this alert (disk full).", Exports.friendlyError("disk full"))
        assertEquals("Blue Iris couldn't export this alert.", Exports.friendlyError(null))
    }

    private fun item(path: String, uri: String = "", status: String = "active") = ExportQueueItem(path, uri, status, 0.0)

    @Test
    fun ourJobIsFoundByPathOrUriExactly() {
        val queue = listOf(item("@1.mp4"), item("@9.mp4", "Export/9.mp4"))
        assertEquals("@9.mp4", ExportQueue.find(queue, ExportStart("@9.mp4", ""), emptySet())?.path)
        assertEquals("@9.mp4", ExportQueue.find(queue, ExportStart("", "Export/9.mp4"), emptySet())?.path)
    }

    @Test
    fun ourJobIsFoundByRecordNumberWhenTheNamesAreWrittenDifferently() {
        val queue = listOf(item("@1.mp4"), item("123", "clips/@123.mp4"))
        assertEquals("123", ExportQueue.find(queue, ExportStart("@123.mp4", ""), emptySet())?.path)
        assertEquals("123", ExportQueue.find(queue, ExportStart("", "Export\\123.mp4"), emptySet())?.path)
    }

    @Test
    fun withNoNameFromBlueIrisTheOneNewEntryIsOurs() {
        val before = setOf(ExportQueue.keyOf(item("@1.mp4")))
        val queue = listOf(item("@1.mp4"), item("@77.mp4"))
        assertEquals("@77.mp4", ExportQueue.find(queue, ExportStart("", ""), before)?.path)
        // Two new entries: cannot tell which is ours.
        assertNull(ExportQueue.find(queue + item("@78.mp4"), ExportStart("", ""), before))
    }

    @Test
    fun theQueueIsDescribedForTheFailureMessage() {
        val text = ExportQueue.describe(listOf(item("@5.mp4", "u", "done")), ExportStart("@9.mp4", ""))
        assertTrue(text.contains("path='@9.mp4'"))
        assertTrue(text.contains("1 item(s)"))
        assertTrue(text.contains("status='done'"))
    }
}
