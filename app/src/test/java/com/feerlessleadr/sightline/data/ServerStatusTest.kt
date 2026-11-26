package com.feerlessleadr.sightline.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerStatusTest {
    private val sample = """
        {"signal":"1","cxns":5,"cpu":36,"gpu":1,"mem":"2.29GB","memphys":"12.9GB","memload":"52%",
         "disks":[{"disk":"E:","folders":"New,Stored","allocated":7485440,"error":0,"used":7169790,"free":460613,"total":7630883}],
         "profile":1,"lock":"1","schedule":"Default","uptime":"4:20:40:11","clips":"Clips: 7076 files","warnings":"0"}
    """.trimIndent()

    @Test
    fun theStatusAnswerIsRead() {
        val s = ServerStatus.parse(Json.parseToJsonElement(sample))
        assertEquals(36.0, s.cpu!!, 0.0)
        assertEquals(1.0, s.gpu!!, 0.0)
        assertEquals(52.0, s.memoryLoad!!, 0.0) // "52%"
        assertEquals("2.29GB", s.memory)
        assertEquals(5, s.connections)
        assertEquals("1", s.profile) // a number in the answer
        assertEquals("Default", s.schedule)
        assertTrue(s.locked)
        assertEquals("green", s.signal)
        assertEquals(0, s.warnings)
        assertEquals(DiskInfo("E:", 7485440, 7169790, 460613, 7630883), s.disks.single())
    }

    @Test
    fun anEmptyOrOddAnswerDoesNotCrash() {
        val s = ServerStatus.parse(null)
        assertEquals(null, s.cpu)
        assertTrue(s.disks.isEmpty())
        assertEquals(0, s.warnings)
        assertFalse(ServerStatus.parse(Json.parseToJsonElement("""{"cpu":"n/a","warnings":"3"}""")).cpu != null)
        assertEquals(3, ServerStatus.parse(Json.parseToJsonElement("""{"warnings":"3"}""")).warnings)
    }

    @Test
    fun aDiskIsSplitIntoRecordingsAllocatedFreeOtherAndFree() {
        // Allocated 100, used 60, free 70 of 200: 60 recordings, 40 allocated and free, 70 other (200-70-60), 30 free beyond.
        val seg = DiskInfo("D:", 100, 60, 70, 200).segments()
        assertEquals(60L, seg.recordings)
        assertEquals(0L, seg.overAllocation)
        assertEquals(40L, seg.allocatedFree)
        assertEquals(70L, seg.other)
        assertEquals(30L, seg.free)
        assertEquals(200L, seg.whole)
        assertEquals("", seg.note)
    }

    @Test
    fun overAllocationAndTooLittleSpaceAreExplained() {
        assertTrue(DiskInfo("D:", 100, 130, 10, 300).segments().note.contains("more recordings"))
        assertEquals(30L, DiskInfo("D:", 100, 130, 10, 300).segments().overAllocation)
        assertTrue(DiskInfo("D:", 500, 100, 50, 1000).segments().note.contains("enough free space"))
        val unmonitored = DiskInfo("D:", 100, 40, 0, 0).segments()
        assertFalse(unmonitored.monitored)
        assertEquals(100L, unmonitored.whole)
    }

    @Test
    fun sizesAndUptimeReadLikeTheWebApp() {
        assertEquals("7.29 TB", ServerNumbers.mib(7_645_000))
        assertEquals("460 GB", ServerNumbers.mib(471_000))
        assertEquals("1.5 GB", ServerNumbers.mib(1_536))
        assertEquals("512 MB", ServerNumbers.mib(512))
        assertEquals("2.3 GB", ServerNumbers.biSize("2.29GB"))
        assertEquals("12.9 GB", ServerNumbers.biSize("12.9GB"))
        assertEquals("4d 20h", ServerNumbers.uptime("4:20:40:11").first)
        assertEquals("20h 40m", ServerNumbers.uptime("0:20:40:11").first)
        assertEquals("40m", ServerNumbers.uptime("0:0:40:11").first)
        assertEquals("?", ServerNumbers.uptime(null).first)
        assertEquals(((4 * 24 + 20) * 3600 + 40 * 60 + 11) * 1000L, ServerNumbers.uptime("4:20:40:11").second)
    }
}
