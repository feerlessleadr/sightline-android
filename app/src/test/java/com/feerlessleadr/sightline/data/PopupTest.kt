package com.feerlessleadr.sightline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupTest {
    private val nowMs = 1_800_000_000_000L
    private val nowSec = nowMs / 1000

    private fun alert(agoSec: Long, memo: String, camera: String = "Front", flags: Long = 0, path: String = "@$agoSec$camera.bvr") =
        RecentAlert(camera, memo, nowSec - agoSec, flags, path)

    private fun tracker() = PopupTracker { nowMs }
    private val people = PopupConfig(kinds = listOf("person"))
    private val everywhere: (String) -> Boolean = { true }

    @Test
    fun offNeverPopsUpAndPlainMotionNeverDoes() {
        assertNull(tracker().consider(listOf(alert(5, "person:90%")), PopupConfig(), everywhere, false))
        assertNull(tracker().consider(listOf(alert(5, "")), PopupConfig(kinds = listOf("person", "vehicle", "animal")), everywhere, false))
    }

    @Test
    fun aChosenKindPopsUpOnceForAnAlertHappeningNow() {
        val t = tracker()
        val a = alert(5, "person:90%")
        val first = t.consider(listOf(a), people, everywhere, false)
        assertEquals("Front", first!!.camera)
        assertEquals(AlertClass.Person, first.tag.cls)
        assertNull(t.consider(listOf(a), people, everywhere, false)) // the same alert again: nothing
    }

    @Test
    fun anOldAlertACameraOffTheGridOrAnotherKindIsIgnored() {
        assertNull(tracker().consider(listOf(alert(45, "person:90%")), people, everywhere, false)) // over 30 s old
        assertNull(tracker().consider(listOf(alert(5, "person:90%", camera = "Back")), people, { it == "Front" }, false))
        assertNull(tracker().consider(listOf(alert(5, "car:90%")), people, everywhere, false))
    }

    @Test
    fun anOlderAlertNeverReplacesANewerOneThatIsShowing() {
        val t = tracker()
        t.consider(listOf(alert(5, "person:90%", path = "new")), people, everywhere, false)
        assertNull(t.consider(listOf(alert(12, "person:80%", camera = "Back", path = "old")), people, everywhere, true))
        // ...but a newer one does.
        assertNotNull(t.consider(listOf(alert(1, "person:70%", camera = "Back", path = "newer")), people, everywhere, true))
    }

    @Test
    fun ofSeveralInTheListTheNewestWins() {
        val t = tracker()
        val e = t.consider(listOf(alert(2, "person:90%", camera = "A"), alert(9, "person:90%", camera = "B")), people, everywhere, false)
        assertEquals("A", e!!.camera)
    }

    @Test
    fun yourOwnLabelsPopUpAndMatchAnyWriting() {
        val config = PopupConfig(labels = listOf("ups"))
        val e = tracker().consider(listOf(alert(5, "UPS:95%")), config, everywhere, false)
        assertEquals("UPS 95%", e!!.tag.text)
        assertEquals(AlertClass.Delivery, e.tag.cls)
        assertNull(tracker().consider(listOf(alert(5, "dog:95%")), config, everywhere, false))
        assertTrue(config.hasLabel("UPS"))
    }

    @Test
    fun theChoiceIsEditedAndSummarised() {
        var c = PopupConfig()
        assertEquals("Off", c.summary())
        c = c.withKind("vehicle", true).withKind("person", true).withLabel("dog", true)
        assertEquals(listOf("person", "vehicle"), c.kinds) // always in the same order
        assertEquals("People, Vehicles, Dogs", c.summary())
        c = c.withKind("person", false).withLabel("Dogs", false)
        assertEquals(PopupConfig(kinds = listOf("vehicle")), c)
        assertFalse(PopupConfig().isOn)
        assertEquals(c, PopupConfig.decode(c.encode()))
        assertEquals(PopupConfig(), PopupConfig.decode("nonsense"))
    }

    @Test
    fun popUpsCanBeSwitchedOffAndOnKeepingTheChoices() {
        val c = PopupConfig(kinds = listOf("person"), labels = listOf("ups"))
        assertTrue(c.isOn)
        val off = c.copy(enabled = false)
        assertFalse(off.isOn)
        assertTrue(off.hasChoices)
        assertEquals("Off (People, UPS)", off.summary())
        assertNull(tracker().consider(listOf(alert(5, "person:90%")), off, everywhere, false))
        assertEquals(off, PopupConfig.decode(off.encode()))
        assertTrue(PopupConfig.decode("{\"kinds\":[\"person\"]}").isOn) // a choice saved before the switch existed stays on
    }

    @Test
    fun aTypedLabelIsCheckedAndAdded() {
        val (c, note) = Popup.addLabel(PopupConfig(), " UPS ", seen = 12, scanned = true)
        assertEquals(listOf("ups"), c.labels)
        assertEquals("Added. “UPS” matched 12 alerts in the last 3 weeks.", note)
        assertEquals("Use letters, numbers, spaces or dashes.", Popup.addLabel(PopupConfig(), "a!b", null, false).second)
        assertTrue(Popup.addLabel(PopupConfig(), "cat", null, true).second.startsWith("Added. Nothing labeled"))
    }
}
