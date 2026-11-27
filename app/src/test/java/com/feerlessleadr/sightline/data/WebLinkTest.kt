package com.feerlessleadr.sightline.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WebLinkTest {
    private val base = "https://bi.example.com/".toHttpUrl()

    @Test
    fun theLinkIsTheServerAddressPlusTheFileName() {
        assertEquals("https://bi.example.com/sightline.htm", WebLink.url(base, "sightline.htm"))
        assertEquals("https://bi.example.com/sightline.htm", WebLink.url(base, " /sightline.htm "))
        assertEquals("https://bi.example.com/apps/sl/index.htm", WebLink.url(base, "apps/sl/index.htm"))
        // A reverse-proxy sub-path in the sign-in address is kept.
        assertEquals("https://bi.example.com/blue/sightline.htm", WebLink.url("https://bi.example.com/blue/".toHttpUrl(), "sightline.htm"))
        assertEquals("sightline.htm", WebLink.DEFAULT_PAGE)
    }

    @Test
    fun anythingThatCouldLeaveTheServerOrCarryDataIsRefused() {
        for (bad in listOf(null, "", "  ", "../x.htm", "a/../b.htm", "https://evil.example/x", "//evil.example/x", "x.htm?user=a", "x.htm#a", "a b.htm", "dir/", "x\\y.htm", "ui3.htm?x=1")) {
            assertNull("should refuse: $bad", WebLink.url(base, bad))
        }
        assertEquals("ui3.htm", WebLink.clean("ui3.htm"))
    }

    @Test
    fun diagnosticsHaveVersionsAndCountsButNoNamesOrAddresses() {
        val cams = listOf(
            Camera(optionValue = "FrontDoor", optionDisplay = "Front Door"),
            Camera(optionValue = "Garage", optionDisplay = "Garage", hidden = true),
            Camera(optionValue = "Old", optionDisplay = "Old", isEnabled = false),
            Camera(optionValue = "index", optionDisplay = "All cameras", group = listOf("FrontDoor", "Garage")),
        )
        val text = Diagnostics.text("0.5.0", "6.1.6.1", "15", 35, "Phone X", cams, listOf("quality" to "Auto", "pop-ups" to "off"))
        assertEquals(
            "Sightline for Android 0.5.0 · Blue Iris 6.1.6.1\n" +
                "Android 15 (API 35) · Phone X\n" +
                "Cameras: 3 in Blue Iris · 1 shown · 1 hidden · 1 disabled · 0 offline · 1 groups\n" +
                "Settings: quality Auto · pop-ups off",
            text,
        )
        for (secret in listOf("FrontDoor", "Front Door", "Garage", "example", "https")) assertFalse(text.contains(secret))
    }
}
