package com.feerlessleadr.sightline.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_800_000_000_000L

    private fun span(daysAgo: Double, lengthHours: Long = 1) = TimeSpan(now - (daysAgo * day).toLong(), lengthHours * hour)
    private fun span(daysAgo: Int, lengthHours: Long = 1) = span(daysAgo.toDouble(), lengthHours)

    @Test
    fun noRecordingsMeansNone() {
        assertTrue(Retention.of(emptyList()).none)
    }

    @Test
    fun footageIsCountedBackUntilAGapOfMoreThanTwoDays() {
        val r = Retention.of(listOf(span(0.5, 2), span(5, 3), span(5.5, 2), span(20, 1), span(21, 1)))
        // The 5-day-old run is more than 2 days before the newest one, so only the newest counts; the rest is "older".
        assertEquals(now - (0.5 * day).toLong(), r.fromMs)
        assertEquals(2.0, r.hours, 0.001)
        assertTrue(r.older)
        assertEquals(now - 21 * day, r.oldestMs)
    }

    @Test
    fun closeRecordingsJoinUpAndOneLongHistoryHasNoOlder() {
        val clips = (0..9).map { span(it.toDouble() + 0.5, 1) } // an hour a day for 10 days
        val r = Retention.of(clips)
        assertEquals(now - (9.5 * day).toLong(), r.fromMs)
        assertEquals(10.0, r.hours, 0.001)
        assertFalse(r.older)
    }

    @Test
    fun theTextsReadLikeTheWebApp() {
        assertEquals("1 h", Retention.kept(10 * 60_000))
        assertEquals("36 h", Retention.kept(36 * hour))
        assertEquals("3 days", Retention.kept(72 * hour))
        assertEquals("14 days", Retention.range(listOf(Retention.of(listOf(span(14, 1)))), now))
        assertEquals("7–30 days", Retention.range(listOf(Retention.of(listOf(span(7, 1))), Retention.of(listOf(span(30, 1)))), now))
        assertEquals("10–16 hours", Retention.range(listOf(Retention.of(listOf(span(10 / 24.0, 1))), Retention.of(listOf(span(16 / 24.0, 1)))), now))
        assertEquals("1.5–30 days", Retention.range(listOf(Retention.of(listOf(span(1.5, 1))), Retention.of(listOf(span(30, 1)))), now))
        assertNull(Retention.range(listOf(Retention.None), now))
    }

    @Test
    fun theFeedReadsEachCameraOnceAndKeepsItForTenMinutes() {
        val asked = ArrayList<String>()
        var t = now
        val feed = RetentionFeed(
            CoroutineScope(Dispatchers.Unconfined),
            { camera, start, end, step ->
                asked += camera
                assertEquals(3_600_000L, step)
                assertEquals(400 * day, end - start)
                if (camera == "Bad") throw BlueIrisException("busy")
                Timeline(listOf(span(3.0, 5)), emptyList())
            },
            { t },
        )
        feed.refresh(listOf("A", "Bad", "B"))
        assertEquals(listOf("A", "Bad", "B"), asked)
        assertEquals(setOf("A", "B"), feed.state.value.byCamera.keys) // the unreadable one is just missing
        assertFalse(feed.state.value.busy)
        feed.refresh(listOf("A"))
        assertEquals(3, asked.size) // fresh: nothing new asked
        t += 11 * 60_000L
        feed.refresh(listOf("A"))
        assertEquals(4, asked.size)
    }
}
