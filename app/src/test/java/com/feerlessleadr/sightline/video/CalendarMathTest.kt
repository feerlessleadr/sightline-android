package com.feerlessleadr.sightline.video

import com.feerlessleadr.sightline.data.AlertMark
import com.feerlessleadr.sightline.data.TimeSpan
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMathTest {
    private val zone = ZoneId.of("America/New_York")
    private val nov = YearMonth.of(2026, 11)

    private fun ms(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDate.of(y, m, d).atTime(h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun currentMonthIsAskedForUpToNow() {
        val now = ms(2026, 11, 25, 12)
        val (s, e) = CalendarMath.monthRange(nov, now, zone)
        assertEquals(ms(2026, 11, 1), s)
        assertEquals(now, e)
        val (_, e2) = CalendarMath.monthRange(YearMonth.of(2026, 10), now, zone)
        assertEquals(ms(2026, 11, 1), e2)
    }

    @Test
    fun aRecordingTouchesEveryDayItSpans() {
        val clips = listOf(TimeSpan(ms(2026, 11, 3, 22), 5 * 3_600_000L)) // 10 PM on the 3rd to 3 AM on the 4th
        val info = CalendarMath.summarise(clips, emptyList(), nov, zone, 0)
        assertEquals(setOf(3, 4), info.recorded)
    }

    @Test
    fun aRecordingEndingExactlyAtMidnightDoesNotTouchTheNextDay() {
        val clips = listOf(TimeSpan(ms(2026, 11, 3, 23), 3_600_000L))
        assertEquals(setOf(3), CalendarMath.summarise(clips, emptyList(), nov, zone, 0).recorded)
    }

    @Test
    fun daysOutsideTheMonthAreLeftOut() {
        val clips = listOf(TimeSpan(ms(2026, 10, 31, 23), 3 * 3_600_000L)) // into 1 Nov
        assertEquals(setOf(1), CalendarMath.summarise(clips, emptyList(), nov, zone, 0).recorded)
    }

    @Test
    fun alertDaysAreTheDaysAlertsStartOn() {
        val alerts = listOf(AlertMark(ms(2026, 11, 7, 9), 5000, 0), AlertMark(ms(2026, 11, 7, 18), 5000, 0), AlertMark(ms(2026, 11, 9, 1), 5000, 0))
        assertEquals(setOf(7, 9), CalendarMath.summarise(emptyList(), alerts, nov, zone, 0).alertDays)
    }

    @Test
    fun futureDaysAndDaysWithoutRecordingsAreDisabledOnceKnown() {
        val today = LocalDate.of(2026, 11, 25)
        val info = MonthInfo(setOf(24, 25), emptySet(), 0)
        assertFalse(CalendarMath.isDisabled(LocalDate.of(2026, 11, 24), today, info))
        assertTrue(CalendarMath.isDisabled(LocalDate.of(2026, 11, 23), today, info))
        assertTrue(CalendarMath.isDisabled(LocalDate.of(2026, 11, 26), today, info))
        // Before the answer arrives only the future is disabled.
        assertFalse(CalendarMath.isDisabled(LocalDate.of(2026, 11, 23), today, null))
        assertTrue(CalendarMath.isDisabled(LocalDate.of(2026, 11, 26), today, null))
    }

    @Test
    fun answersAreKeptFiveMinutesForTheCurrentMonthAndAnHourForOlderOnes() {
        val now = ms(2026, 11, 25, 12)
        val info = MonthInfo(emptySet(), emptySet(), now - 4 * 60_000L)
        assertTrue(CalendarMath.isFresh(info, nov, now, zone))
        assertFalse(CalendarMath.isFresh(info.copy(loadedAtMs = now - 6 * 60_000L), nov, now, zone))
        val old = MonthInfo(emptySet(), emptySet(), now - 50 * 60_000L)
        assertTrue(CalendarMath.isFresh(old, YearMonth.of(2026, 10), now, zone))
    }

    @Test
    fun theGridStartsOnASunday() {
        assertEquals(0, CalendarMath.leadingBlanks(nov)) // 1 Nov 2026 is a Sunday
        assertEquals(3, CalendarMath.leadingBlanks(YearMonth.of(2026, 7))) // 1 Jul 2026 is a Wednesday
    }

    @Test
    fun aToTimeBeforeTheFromTimeRunsIntoTheNextDay() {
        val d = LocalDate.of(2026, 11, 10)
        val (s, e) = CalendarMath.range(d, 22 * 60, 2 * 60, zone)
        assertEquals(ms(2026, 11, 10, 22), s)
        assertEquals(ms(2026, 11, 11, 2) + 59_000L, e)
        val (s2, e2) = CalendarMath.range(d, 9 * 60, 10 * 60, zone)
        assertEquals(ms(2026, 11, 10, 9), s2)
        assertEquals(ms(2026, 11, 10, 10) + 59_000L, e2)
    }
}
