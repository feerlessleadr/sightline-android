package com.feerlessleadr.sightline.video

import com.feerlessleadr.sightline.data.AlertMark
import com.feerlessleadr.sightline.data.TimeSpan
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.YearMonth

/** What the calendar knows about one month of one camera: days with recordings and days with alerts (day numbers 1..31). */
data class MonthInfo(val recorded: Set<Int>, val alertDays: Set<Int>, val loadedAtMs: Long)

/** The numbers behind the "go to date and time" calendar, kept apart from the screen so they can be tested. */
object CalendarMath {
    /** The timeline is asked for in steps of one hour for a whole month. */
    const val STEP_MS = 3_600_000L

    /** The period to ask the timeline for: from the first of the month to the end of it, or to [nowMs] for the current month. */
    fun monthRange(month: YearMonth, nowMs: Long, zone: ZoneId): Pair<Long, Long> {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to minOf(end, nowMs)
    }

    /**
     * The days of [month] that a recorded stretch touches, and the days an alert starts on. Spans come as in the
     * timeline reply (start and length in milliseconds).
     */
    fun summarise(clips: List<TimeSpan>, alerts: List<AlertMark>, month: YearMonth, zone: ZoneId, nowMs: Long): MonthInfo {
        fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val recorded = HashSet<Int>()
        for (c in clips) {
            var d = day(c.startMs)
            val last = day(maxOf(c.startMs, c.startMs + c.lengthMs - 1))
            while (!d.isAfter(last)) {
                if (YearMonth.from(d) == month) recorded += d.dayOfMonth
                d = d.plusDays(1)
            }
        }
        val alertDays = HashSet<Int>()
        for (a in alerts) {
            val d = day(a.startMs)
            if (YearMonth.from(d) == month) alertDays += d.dayOfMonth
        }
        return MonthInfo(recorded, alertDays, nowMs)
    }

    /** How long a month's answer is kept: 5 minutes for the current month (it is still growing), an hour for older ones. */
    fun isFresh(info: MonthInfo, month: YearMonth, nowMs: Long, zone: ZoneId): Boolean {
        val current = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone)) == month
        return nowMs - info.loadedAtMs < if (current) 300_000L else 3_600_000L
    }

    /** True if a day should be greyed out: in the future, or (once known) with no recordings. */
    fun isDisabled(date: LocalDate, today: LocalDate, info: MonthInfo?): Boolean =
        date.isAfter(today) || (info != null && date.dayOfMonth !in info.recorded)

    /** Empty cells before the 1st so the grid starts on a Sunday, like the web app's calendar. */
    fun leadingBlanks(month: YearMonth): Int = month.atDay(1).dayOfWeek.value % 7

    /**
     * The period for a chosen day and from/to clock times (minutes after midnight). The end covers the whole last
     * minute, and a "to" at or before the "from" runs into the next day (10 PM to 2 AM).
     */
    fun range(date: LocalDate, fromMinutes: Int, toMinutes: Int, zone: ZoneId): Pair<Long, Long> {
        fun at(minutes: Int, seconds: Int): Long =
            LocalDateTime.of(date, java.time.LocalTime.of(minutes / 60, minutes % 60, seconds)).atZone(zone).toInstant().toEpochMilli()
        val start = at(fromMinutes, 0)
        var end = at(toMinutes, 59)
        if (end <= start) end += 86_400_000L
        return start to end
    }
}
