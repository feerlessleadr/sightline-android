package com.feerlessleadr.sightline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTagsTest {
    @Test
    fun aiLabelWithPercentage() {
        val tags = AlertTags.of("person:90%", 0)
        assertEquals(listOf(AlertTag(AlertClass.Person, "Person 90%")), tags)
    }

    @Test
    fun twoLabelsAreKeptInOrderAndClassified() {
        val tags = AlertTags.of("dog:88%,car:55%", 0)
        assertEquals(AlertTag(AlertClass.Animal, "Dog 88%"), tags[0])
        assertEquals(AlertTag(AlertClass.Vehicle, "Car 55%"), tags[1])
    }

    @Test
    fun deliveryCompaniesGetTheirOwnClassAndSpelling() {
        assertEquals(AlertTag(AlertClass.Delivery, "FedEx 70%"), AlertTags.of("fedex:70%", 0)[0])
        assertEquals(AlertClass.Delivery, AlertTags.classify("ups"))
    }

    @Test
    fun cameraEventNamesReadAsPlainWords() {
        assertEquals(AlertTag(AlertClass.Person, "Person"), AlertTags.of("PeopleDet", 0)[0])
        assertEquals(AlertTag(AlertClass.Vehicle, "Vehicle"), AlertTags.of("VehicleDet", 0)[0])
        assertEquals(AlertTag(AlertClass.Motion, "Line crossed"), AlertTags.of("LineDetector", 0)[0])
    }

    @Test
    fun emptyTextFallsBackToTheFlagBits() {
        assertEquals(AlertClass.Person, AlertTags.of("", 1L shl 26).first().cls)
        assertEquals(AlertClass.Vehicle, AlertTags.of("", 1L shl 27).first().cls)
        // The wildlife bit is above bit 30; it needs a 64-bit number.
        assertEquals(AlertClass.Animal, AlertTags.of("", 1L shl 31).first().cls)
        assertEquals(AlertTag(AlertClass.Motion, "Motion"), AlertTags.of("", 0).first())
    }

    @Test
    fun nothingFoundIsIgnored() {
        assertEquals(AlertClass.Motion, AlertTags.of("nothing found", 0).first().cls)
    }

    @Test
    fun aiResultIsFreshFor45SecondsOr3MinutesWhileTriggered() {
        val tag = CameraTag(AlertTag(AlertClass.Person, "Person"), atMillis = 0)
        assertTrue(tag.isFresh(44_000, triggered = false))
        assertFalse(tag.isFresh(46_000, triggered = false))
        assertTrue(tag.isFresh(170_000, triggered = true))
        assertFalse(tag.isFresh(181_000, triggered = true))
    }

    @Test
    fun mergeKeepsTheNewestAiResultAndSkipsMotionOnlyAndHiddenCameras() {
        val alerts = listOf(
            RecentAlert("Front", "person:91%", 200, 0),
            RecentAlert("Front", "car:60%", 100, 0), // older: ignored
            RecentAlert("Back", "", 300, 0), // motion only: ignored
            RecentAlert("Garage", "dog:80%", 250, 0), // not shown: ignored
        )
        val merged = mergeTags(emptyMap(), alerts) { it != "Garage" }
        assertEquals(setOf("front"), merged.keys)
        assertEquals("Person 91%", merged.getValue("front").tag.text)
        assertEquals(200_000L, merged.getValue("front").atMillis)
    }
}
