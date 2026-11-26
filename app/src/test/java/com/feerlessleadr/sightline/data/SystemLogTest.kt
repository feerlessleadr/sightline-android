package com.feerlessleadr.sightline.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemLogTest {
    private val sample = Json.parseToJsonElement(
        """
        [{"level":0,"obj":"Driveway","msg":"Motion started","date":1795500000,"count":1},
         {"level":2,"obj":"Pool","msg":"Lost signal","date":"1795500200","count":"3"},
         {"level":1,"obj":"Disk","msg":"E: is nearly full","date":1795500100},
         {"level":4,"obj":"Pool","msg":"Signal restored","date":1795500300,"count":0}]
        """.trimIndent(),
    )

    @Test
    fun theLogIsReadNewestFirst() {
        val log = SystemLog.parse(sample)
        assertEquals(listOf("Signal restored", "Lost signal", "E: is nearly full", "Motion started"), log.map { it.message })
        assertEquals(3, log[1].count) // numbers sent as text
        assertEquals(1, log[0].count) // a count of 0 or none is 1
        assertTrue(SystemLog.parse(null).isEmpty())
    }

    @Test
    fun warningsErrorsAlertsAndQuestionsAreProblems() {
        val log = SystemLog.parse(sample)
        assertEquals(2, SystemLog.count(log, LogFilter.Problems))
        assertEquals(2, SystemLog.count(log, LogFilter.Info))
        assertEquals(4, SystemLog.count(log, LogFilter.All))
        assertTrue(SystemLog.isProblem(5))
        assertFalse(SystemLog.isProblem(4))
    }

    @Test
    fun searchLooksInTheSourceAndTheMessageIgnoringCase() {
        val log = SystemLog.parse(sample)
        assertEquals(listOf("Signal restored", "Lost signal"), log.filter { SystemLog.matches(it, LogFilter.All, " POOL ") }.map { it.message })
        assertEquals(listOf("Lost signal"), log.filter { SystemLog.matches(it, LogFilter.Problems, "pool") }.map { it.message })
        assertEquals("Error", SystemLog.levelName(2))
        assertEquals("Level 7", SystemLog.levelName(7))
    }
}
