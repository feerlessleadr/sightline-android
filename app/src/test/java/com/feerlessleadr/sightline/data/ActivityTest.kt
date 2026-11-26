package com.feerlessleadr.sightline.data

import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTest {
    private val zone = ZoneOffset.UTC
    // 2026-11-20 15:00 UTC
    private val nowMs = java.time.ZonedDateTime.of(2026, 11, 20, 15, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private fun alert(timeMs: Long, memo: String = "", camera: String = "Front", path: String = "@$timeMs$camera.bvr", flags: Long = 0) =
        AlertItem(camera, memo, timeMs, flags, path, path, 0, "10 sec")

    @Test
    fun alertsAreCountedPerDayAndByKind() {
        val items = listOf(
            alert(nowMs - 1 * 3_600_000, "person:90%"), // today 14:00
            alert(nowMs - 2 * 3_600_000, "car:80%"),
            alert(nowMs - day, "person:80%"), // yesterday 15:00
            alert(nowMs - 10 * day, "person:80%"), // outside 7 days
        )
        val r = ActivityReport.build(items, "all", 7, nowMs, zone)
        assertEquals(7, r.days.size)
        assertEquals(2, r.days.last().total) // today is last
        assertEquals(1, r.days.last().byClass[AlertClass.Person])
        assertEquals(1, r.days.last().byClass[AlertClass.Vehicle])
        assertEquals(1, r.days[5].total) // yesterday
        assertEquals(4, r.total) // the old one still counts in the by-hour table
    }

    @Test
    fun theTypeFilterKeepsOnlyThatKind() {
        val items = listOf(alert(nowMs - 3_600_000, "person:90%"), alert(nowMs - 7_200_000, "car:80%"))
        assertEquals(1, ActivityReport.build(items, "people", 7, nowMs, zone).total)
        assertEquals(1, ActivityReport.build(items, "vehicles", 7, nowMs, zone).total)
        assertEquals(0, ActivityReport.build(items, "animals", 7, nowMs, zone).total)
    }

    @Test
    fun camerasAreRankedAndHoursCounted() {
        val half = nowMs - 5_400_000 // 13:30
        val items = listOf(
            alert(half, "person:90%", camera = "Front"),
            alert(half - 1000, "person:90%", camera = "Front", path = "b"),
            alert(nowMs - 20 * 3_600_000, "person:90%", camera = "Back"), // yesterday 19:00
        )
        val r = ActivityReport.build(items, "all", 7, nowMs, zone)
        assertEquals(listOf("Front", "Back"), r.cameras.map { it.camera })
        assertEquals(2, r.cameras[0].hours[13])
        assertEquals(1, r.cameras[1].hours[19])
        assertEquals(3, r.total)
    }

    @Test
    fun theBusiestAndQuietestHoursAreFound() {
        val half = nowMs - 5_400_000 // 13:30
        val items = (0 until 3).map { alert(half - it, "person:90%", path = "p$it") } + alert(nowMs - 5 * 3_600_000, "person:90%", path = "q") // and 10:00
        val r = ActivityReport.build(items, "all", 7, nowMs, zone)
        assertEquals(13, r.busiestHour)
        assertEquals(0, r.quietestHour) // the first hour nothing happened in
    }

    @Test
    fun theFeedReadsEachDayAndKeepsTheResultForTenMinutes() {
        val calls = ArrayList<Triple<String, Long, Long>>()
        val all = listOf(alert(nowMs - 3_600_000, "person:90%"), alert(nowMs - 2 * day, "car:80%", camera = "Hidden"), alert(nowMs - 2 * day - 1000, "dog:80%", camera = "Back"))
        var now = nowMs
        val feed = ActivityFeed(
            CoroutineScope(Dispatchers.Unconfined),
            { camera, s, e -> calls += Triple(camera, s, e); all.filter { it.timeMs / 1000 in s..e } },
            { it != "Hidden" },
            { now },
        )
        feed.open()
        val ui = feed.state.value
        assertTrue(!ui.loading)
        assertEquals(7, calls.size)
        assertEquals(2, ui.data[7]!!.items.size) // the hidden camera is left out
        feed.open()
        assertEquals(7, calls.size) // fresh: nothing new asked
        feed.setDays(14)
        assertEquals(21, calls.size) // 14 more days for the longer choice
        assertNotNull(feed.state.value.data[14])
        now += 11 * 60_000L
        feed.setDays(7)
        assertEquals(28, calls.size) // old: read again
    }

    @Test
    fun aBusyDayOfOneHundredIsReadOnFromItsOldestAlert() {
        val many = (1..150).map { alert(nowMs - it * 60_000L, "person:90%", path = "m$it") } // 150 in the last 150 minutes
        val feed = ActivityFeed(
            CoroutineScope(Dispatchers.Unconfined),
            { _, s, e -> many.filter { it.timeMs / 1000 in s..e }.sortedByDescending { it.timeMs }.take(100) },
            { true },
            { nowMs },
        )
        feed.open()
        assertEquals(150, feed.state.value.data[7]!!.items.size)
    }
}
