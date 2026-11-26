package com.feerlessleadr.sightline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test
    fun linksOpenLiveViewsAlertsAndTheAlertList() {
        assertEquals(DeepLink.Live("FrontDoor"), DeepLink.parse("sightline://live/FrontDoor"))
        assertEquals(DeepLink.Live("Front Door"), DeepLink.parse("sightline://live/Front%20Door"))
        assertEquals(DeepLink.Cameras, DeepLink.parse("sightline://live"))
        assertEquals(DeepLink.Alerts, DeepLink.parse("sightline://alerts"))
        assertEquals(DeepLink.Alert("FrontDoor", "@1234567.bvr"), DeepLink.parse("sightline://alert/FrontDoor/@1234567.bvr"))
        assertEquals(DeepLink.Alert("FrontDoor", "@1234567.bvr"), DeepLink.parse("SIGHTLINE://alert/FrontDoor/%401234567.bvr"))
    }

    @Test
    fun anythingElseIsIgnored() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse(""))
        assertNull(DeepLink.parse("https://example.com/live/FrontDoor"))
        assertNull(DeepLink.parse("sightline://unknown/x"))
        assertNull(DeepLink.parse("sightline://alert/FrontDoor")) // no id
        assertNull(DeepLink.parse("sightline://live/a/b"))
        assertNull(DeepLink.parse("sightline://live/..%2F..%2Fx")) // path tricks
        assertNull(DeepLink.parse("sightline://live/<script>"))
        assertNull(DeepLink.parse("sightline://alerts/extra"))
    }
}
