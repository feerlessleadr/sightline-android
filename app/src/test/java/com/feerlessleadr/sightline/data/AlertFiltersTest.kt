package com.feerlessleadr.sightline.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFiltersTest {
    private fun alert(memo: String = "", flags: Long = 0, path: String = "@1.bvr", camera: String = "Cam1") =
        AlertItem(camera, memo, 1_800_000_000_000L, flags, path, "@1.bvr", 0, "10 sec")

    @Test
    fun peopleVehiclesAndAnimalsUseTheAiFlagsOrTheLabel() {
        assertTrue(AlertFilters.matches("people", alert(flags = 1L shl 26)))
        assertTrue(AlertFilters.matches("people", alert("person:91%")))
        assertTrue(AlertFilters.matches("people", alert("PeopleDet"))) // a camera's own detection
        assertTrue(AlertFilters.matches("people", alert("face")))
        assertFalse(AlertFilters.matches("people", alert("car:80%")))
        assertTrue(AlertFilters.matches("vehicles", alert("truck:70%")))
        assertTrue(AlertFilters.matches("vehicles", alert(flags = 1L shl 27)))
        assertFalse(AlertFilters.matches("vehicles", alert("dog:80%")))
        assertTrue(AlertFilters.matches("animals", alert("dog:88%")))
        assertTrue(AlertFilters.matches("animals", alert(flags = 1L shl 31)))
        assertTrue(AlertFilters.matches("animals", alert("AnimalDet")))
        assertFalse(AlertFilters.matches("animals", alert("person")))
        assertTrue(AlertFilters.matches("all", alert()))
    }

    @Test
    fun flaggedUsesTheFlagsIncludingChangesMadeOnThePhone() {
        val a = alert(flags = 0)
        assertFalse(AlertFilters.matches("flagged", a))
        assertTrue(AlertFilters.matches("flagged", a, flags = 1L shl 1))
        assertTrue(AlertFilters.matches("flagged", alert(flags = 1L shl 1)))
    }

    @Test
    fun labelsIgnoreCapitalisationAndPlurals() {
        assertEquals("dog", AlertFilters.normLabel("Dogs"))
        assertEquals("fedex", AlertFilters.normLabel("Fed Ex"))
        assertEquals("fedex", AlertFilters.normLabel("FedEx"))
        assertEquals("box", AlertFilters.normLabel("boxes"))
        assertEquals("puppy", AlertFilters.normLabel("puppies"))
        assertTrue(AlertFilters.matches("label:dog", alert("Dog:94%")))
        assertTrue(AlertFilters.matches("label:dogs", alert("dog:94%,car:50%")))
        assertTrue(AlertFilters.matches("label:ups", alert("UPS:77%")))
        assertFalse(AlertFilters.matches("label:cat", alert("dog:94%")))
        assertEquals(listOf("person", "car"), AlertFilters.memoNames("person:91%,car:55%"))
    }

    @Test
    fun severalSelectedFiltersMatchAlertsThatMatchAnyOfThem() {
        val dog = alert("dog:80%")
        val car = alert("car:80%")
        val ids = listOf("people", AlertFilters.label("dog"))
        assertTrue(AlertFilters.matchesAny(ids, dog))
        assertFalse(AlertFilters.matchesAny(ids, car))
        assertTrue(AlertFilters.matchesAny(emptyList(), car)) // none selected = All
    }

    @Test
    fun buttonsAndMessagesUseTheWebAppsNames() {
        assertEquals("People", AlertFilters.name("people"))
        assertEquals("Dogs", AlertFilters.name("label:dog"))
        assertEquals("UPS", AlertFilters.name("label:ups"))
        assertEquals("Foxes", AlertFilters.name("label:fox"))
        assertEquals("Puppies", AlertFilters.name("label:puppy"))
        assertEquals("Geese", AlertFilters.name("label:goose"))
        assertEquals("alerts", AlertFilters.selectionNoun(emptyList()))
        assertEquals("dog or person alerts", AlertFilters.selectionNoun(listOf("label:dog", "people")))
    }

    @Test
    fun timeRangesFollowTheWebApp() {
        val zone = ZoneId.of("America/New_York")
        val now = LocalDate.of(2026, 11, 25).atTime(15, 30).atZone(zone).toEpochSecond()
        val midnight = LocalDate.of(2026, 11, 25).atStartOfDay(zone).toEpochSecond()
        assertEquals(AlertRange(now - 3600, now + 60), AlertRange.lastHour(now))
        assertEquals(AlertRange(midnight, midnight + 86_399), AlertRange.today(now, zone))
        assertEquals(AlertRange(midnight - 86_400, midnight - 1), AlertRange.yesterday(now, zone))
        assertEquals(AlertRange(midnight - 4 * 3600, midnight + 6 * 3600), AlertRange.lastNight(now, zone)) // 8 PM to 6 AM
        assertEquals(AlertRange(midnight - 86_400, midnight - 1), AlertRange.day(LocalDate.of(2026, 11, 24), zone))
    }
}
