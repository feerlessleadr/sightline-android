package com.feerlessleadr.sightline.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFilterSettingsTest {
    private val nowMs = 1_800_000_000_000L
    private val nowSec = nowMs / 1000

    private fun alert(agoSec: Long, memo: String, flags: Long = 0, camera: String = "Cam1") =
        AlertItem(camera, memo, (nowSec - agoSec) * 1000, flags, "@$agoSec.bvr", "@$agoSec.bvr", 0, "10 sec")

    private fun settings(all: List<AlertItem>, saved: MutableList<AlertFilterConfig> = ArrayList(), start: AlertFilterConfig = AlertFilterConfig()) =
        AlertFilterSettings(
            CoroutineScope(Dispatchers.Unconfined),
            { _, s, e -> all.filter { it.timeMs / 1000 in s..e } },
            { it != "Hidden" },
            { nowMs },
            { start },
            { saved += it },
        )

    @Test
    fun chooseModeShowsTheDefaultsAndTurningOneOffRemovesIt() {
        val s = settings(emptyList())
        assertEquals(AlertFilters.DEFAULT, s.config.chips())
        s.toggle(AlertFilters.ANIMALS)
        assertEquals(listOf("all", "people", "vehicles", "flagged"), s.config.chips())
    }

    @Test
    fun aLabelAddedByTheOwnerIsTurnedOnAndMatchesAnyWriting() {
        val s = settings(emptyList())
        val note = s.addLabel("  Dogs ")
        assertEquals("Added.", note)
        assertTrue("label:dogs" in s.config.chips())
        s.toggle("label:dog") // the same word, written differently: turns it off
        assertFalse("label:dogs" in s.config.chips())
        assertEquals("Use letters, numbers, spaces or dashes.", s.addLabel("a!b"))
        assertEquals(listOf("dogs"), s.config.labels)
    }

    @Test
    fun theScanCountsLabelsOverThreeWeeksAndAutoShowsTheCommonOnes() {
        val items = listOf(
            alert(3_600, "person:90%"), alert(7_200, "person:80%"),
            alert(86_400 + 100, "car:70%"),
            alert(2 * 86_400 + 100, "UPS:95%"), alert(3 * 86_400 + 100, "ups:90%"), alert(4 * 86_400 + 100, "UPS:88%"),
            alert(5 * 86_400 + 100, "dog:90%"),
            alert(6 * 86_400 + 100, "cat:90%", camera = "Hidden"),
            alert(30 * 86_400, "bear:90%"), // beyond 3 weeks
        )
        val saved = ArrayList<AlertFilterConfig>()
        val s = settings(items, saved)
        s.setAuto(true)
        val ui = s.state.value
        assertFalse(ui.scanning)
        val counts = ui.counts!!
        assertEquals(3, counts[AlertFilters.normLabel("ups")]!!.n)
        assertNull(counts["bear"]) // too old
        assertNull(counts["cat"]) // hidden camera
        assertEquals(5, s.config.chips().size)
        assertEquals("all", s.config.chips().first())
        assertEquals("label:ups", s.config.chips()[1]) // most common first after All
        assertTrue(saved.isNotEmpty())
    }

    @Test
    fun anAlwaysShownButtonIsNotRepeatedAmongTheRecentOnes() {
        val items = listOf(alert(3_600, "person:90%"), alert(7_200, "car:80%"))
        val s = settings(items)
        s.setAuto(true)
        s.toggle(AlertFilters.PEOPLE)
        val chips = s.config.chips()
        assertEquals(1, chips.count { it == "people" })
        assertEquals("people", chips[1]) // pins come right after All
        s.setCount(1)
        assertEquals(3, s.config.chips().size)
    }

    @Test
    fun theConfigSurvivesBeingSavedAndRead() {
        val c = AlertFilterConfig(auto = true, pins = listOf("people", "label:dog"), count = 3, labels = listOf("dog"), recent = listOf(RecentFilter("vehicles", 4)))
        assertEquals(c, AlertFilterConfig.decode(c.encode()))
        assertEquals(AlertFilterConfig(), AlertFilterConfig.decode("not json"))
        assertEquals(AlertFilterConfig(), AlertFilterConfig.decode(null))
    }

    @Test
    fun aSelectedButtonThatIsHiddenIsDropped() {
        val a = alert(3_600, "person:90%")
        val feed = AlertsFeed(CoroutineScope(Dispatchers.Unconfined), { _, _, _ -> listOf(a) }, { nowMs }, { true }, retryDelayMs = 0)
        feed.reload()
        feed.select("people", false)
        feed.retainFilters(listOf("all", "vehicles"))
        assertTrue(feed.state.value.filters.isEmpty())
        assertNotNull(feed.state.value.items.firstOrNull())
    }

    @Test
    fun labelsMapToTheirButtons() {
        assertEquals("people", LabelTally.recentId("person"))
        assertEquals("vehicles", LabelTally.recentId("Trucks"))
        assertEquals("label:ups", LabelTally.recentId("ups"))
        assertEquals("label:fed ex", LabelTally.recentId("fed_ex"))
        assertNull(LabelTally.recentId("motion"))
    }
}
