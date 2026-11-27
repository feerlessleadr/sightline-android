package com.feerlessleadr.sightline.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsFeedTest {
    private val nowMs = 1_800_000_000_000L
    private val nowSec = nowMs / 1000
    private val hour = 3600L

    private fun alert(agoSec: Long, memo: String = "", flags: Long = 0, camera: String = "Cam1", path: String = "@$agoSec.bvr") =
        AlertItem(camera, memo, (nowSec - agoSec) * 1000, flags, path, path, 0, "10 sec")

    private class Fake(val all: List<AlertItem>) {
        val calls = ArrayList<Triple<String, Long, Long>>()
        var failures = 0
        suspend fun fetch(camera: String, start: Long, end: Long): List<AlertItem> {
            calls += Triple(camera, start, end)
            if (failures > 0) {
                failures--
                throw BlueIrisException("busy")
            }
            // Like Blue Iris: at most the newest 100.
            return all.filter { it.timeMs / 1000 in start..end && (camera == "index" || it.camera == camera) }
                .sortedByDescending { it.timeMs }.take(100)
        }
    }

    private fun feed(fake: Fake, shown: (String) -> Boolean = { true }) =
        AlertsFeed(CoroutineScope(Dispatchers.Unconfined), fake::fetch, { nowMs }, shown, retryDelayMs = 0)

    @Test
    fun alertsComeNewestFirstAndOlderOnesLoadAsYouGoBack() {
        val fake = Fake(listOf(alert(2 * hour), alert(30 * hour), alert(5 * hour)))
        val f = feed(fake)
        f.reload()
        assertEquals(listOf(2 * hour, 5 * hour), f.state.value.items.map { nowSec - it.timeMs / 1000 })
        assertEquals(1, f.state.value.reloads)
        f.select("people", combine = false)
        assertEquals(2, f.state.value.reloads) // a new filter starts the list again
        f.select(AlertFilters.ALL, combine = false)
        assertFalse(f.state.value.loading)
        f.loadMore()
        assertEquals(3, f.state.value.items.size)
        assertEquals(30 * hour, nowSec - f.state.value.items.last().timeMs / 1000)
    }

    @Test
    fun aQuietWeekEndsWithAnOfferToLookFurtherBack() {
        val fake = Fake(listOf(alert(10 * 86_400L)))
        val f = feed(fake)
        f.reload()
        assertTrue(f.state.value.items.isEmpty())
        assertEquals(AlertsEnd.QuietWeek, f.state.value.end)
        assertTrue(f.state.value.exhausted)
        assertEquals(7, fake.calls.size) // a week, a day at a time
        f.lookFurtherBack()
        assertEquals(1, f.state.value.items.size)
    }

    @Test
    fun aTimeRangeStopsAtItsStartAndKeepsToItsEnd() {
        val fake = Fake(listOf(alert(1 * hour), alert(3 * hour), alert(10 * hour)))
        val f = feed(fake)
        f.setRange(AlertRange(nowSec - 4 * hour, nowSec))
        assertEquals(listOf(1 * hour, 3 * hour), f.state.value.items.map { nowSec - it.timeMs / 1000 })
        f.loadMore()
        assertEquals(AlertsEnd.RangeEnd, f.state.value.end)
        assertTrue(f.state.value.exhausted)
        // Nothing outside the range was asked for.
        assertTrue(fake.calls.all { it.second >= nowSec - 4 * hour })
    }

    @Test
    fun filtersAndHiddenCamerasAreAppliedOnThePhone() {
        val fake = Fake(listOf(alert(1 * hour, "person:90%"), alert(2 * hour, "car:90%"), alert(3 * hour, "dog:90%", camera = "Hidden")))
        val f = feed(fake) { it != "Hidden" }
        f.reload()
        assertEquals(2, f.state.value.items.size)
        f.select("people", combine = false)
        assertEquals(listOf("person:90%"), f.state.value.items.map { it.memo })
        f.select("vehicles", combine = true)
        assertEquals(setOf("person:90%", "car:90%"), f.state.value.items.map { it.memo }.toSet())
        f.select("people", combine = true) // toggles it off again
        assertEquals(listOf("vehicles"), f.state.value.filters)
        f.select(AlertFilters.ALL, combine = false)
        assertTrue(f.state.value.filters.isEmpty())
        assertEquals(2, f.state.value.items.size)
    }

    @Test
    fun aCameraChoiceAsksForThatCamera() {
        val fake = Fake(listOf(alert(1 * hour, camera = "A"), alert(2 * hour, camera = "B")))
        val f = feed(fake)
        f.setCamera("B")
        assertEquals(listOf("B"), f.state.value.items.map { it.camera })
        assertEquals("B", fake.calls.last().first)
    }

    @Test
    fun aFullPageContinuesFromTheOldestAlertInsteadOfSkippingToTheNextDay() {
        val many = (1..100).map { alert(it * 60L) } // 100 alerts in the last 100 minutes
        val fake = Fake(many + alert(20 * hour))
        val f = feed(fake)
        f.reload()
        assertEquals(100, f.state.value.items.size)
        f.loadMore()
        // The second request ends where the first page's oldest alert was, not a day back.
        assertEquals(nowSec - 100 * 60L, fake.calls[1].third)
        assertEquals(101, f.state.value.items.size)
    }

    @Test
    fun aRefusedListIsAskedOnceMoreThenReportedAndCanBeRetried() {
        val fake = Fake(listOf(alert(1 * hour)))
        fake.failures = 2
        val f = feed(fake)
        f.reload()
        assertEquals(AlertsEnd.Failed, f.state.value.end)
        assertEquals("Could not load alerts: busy", f.state.value.error)
        assertEquals(2, fake.calls.size)
        f.retry()
        assertEquals(1, f.state.value.items.size)
        assertEquals(null, f.state.value.error)
    }

    @Test
    fun aFlagChangedHereShowsAtOnceAndAReloadForgetsIt() {
        val a = alert(1 * hour, path = "@42.bvr")
        val fake = Fake(listOf(a))
        val f = feed(fake)
        f.reload()
        assertEquals(0L, f.state.value.flagsOf(a))
        f.noteFlags("@42.bvr", 1L shl 1)
        assertEquals(1L shl 1, f.state.value.flagsOf(a))
        assertNotNull(f.state.value.flagOverrides["42"])
        f.reload()
        assertEquals(0L, f.state.value.flagsOf(a))
    }

    @Test
    fun theListIsLoadedOnceAndAgainWhenOld() {
        var now = nowMs
        val fake = Fake(listOf(alert(1 * hour)))
        val f = AlertsFeed(CoroutineScope(Dispatchers.Unconfined), fake::fetch, { now }, { true }, 0)
        f.ensureLoaded()
        val first = fake.calls.size
        f.ensureLoaded()
        assertEquals(first, fake.calls.size) // fresh: nothing new asked
        now += 6 * 60_000L
        f.ensureLoaded()
        assertTrue(fake.calls.size > first)
    }
}
