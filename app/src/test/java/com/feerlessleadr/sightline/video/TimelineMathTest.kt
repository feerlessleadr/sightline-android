package com.feerlessleadr.sightline.video

import com.feerlessleadr.sightline.data.Timeline
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMathTest {
    private val now = 1_800_000_000_000L

    @Test
    fun stepSizeIsAPowerOfTwoWithAbout500StepsAcrossTheWindow() {
        assertEquals(256L, TimelineMath.msecPerStep(120_000)) // 2 minutes: 240 -> 2^8
        assertEquals(64L, TimelineMath.msecPerStep(30_000)) // never below 2^6
        assertEquals(8192L, TimelineMath.msecPerStep(3_600_000)) // 1 hour: 7200 -> 2^13
    }

    @Test
    fun labelsAreSpacedSoThereAreAtMostSix() {
        assertEquals(30_000L, TimelineMath.labelStep(120_000)) // 2 min -> 30 s
        assertEquals(600_000L, TimelineMath.labelStep(3_600_000)) // 1 h -> 10 min
        assertEquals(172_800_000L, TimelineMath.labelStep(7L * 86_400_000L))
    }

    @Test
    fun liveWindowEndsFourPercentAfterNow() {
        val w = TimelineMath.liveWindow(900_000, now)
        assertEquals(900_000L, w.length)
        assertEquals(now + 36_000, w.end)
    }

    @Test
    fun centeredWindowDoesNotReachFarIntoTheFuture() {
        val w = TimelineMath.centered(now, 900_000, now)
        assertEquals(now + 36_000, w.end)
        val past = TimelineMath.centered(now - 3_600_000, 900_000, now)
        assertEquals(now - 3_600_000 + 450_000, past.end)
        assertEquals(900_000L, past.length)
    }

    @Test
    fun windowFollowsThePlayheadNearTheRightEdge() {
        val w = TimeWindow(now - 3_600_000, now - 600_000)
        assertTrue(TimelineMath.shouldFollow(w.start + (w.length * 0.9).toLong(), w, now))
        assertTrue(!TimelineMath.shouldFollow(w.start + w.length / 2, w, now))
    }

    @Test
    fun pinchingChangesLengthAroundTheFinger() {
        val w = TimeWindow(now - 7_200_000, now - 3_600_000) // 1 hour
        val anchor = w.start + w.length / 2
        val z = TimelineMath.zoomed(w, 2f, anchor, now)
        assertEquals(1_800_000L, z.length)
        assertEquals(anchor, z.start + z.length / 2)
        // Never shorter than 30 s or longer than 7 days.
        assertEquals(30_000L, TimelineMath.zoomed(w, 1000f, anchor, now).length)
        assertEquals(TimelineMath.MAX_WINDOW_MS, TimelineMath.zoomed(w, 0.001f, anchor, now).length)
    }

    @Test
    fun aFrameLastsAsLongAsTheCameraFrameRateSays() {
        assertEquals(100L, TimelineMath.frameIntervalMs(null)) // unknown: 10 a second
        assertEquals(100L, TimelineMath.frameIntervalMs(0.0))
        assertEquals(200L, TimelineMath.frameIntervalMs(5.0))
        assertEquals(33L, TimelineMath.frameIntervalMs(30.0))
    }

    @Test
    fun steppingForwardNeverGoesIntoLive() {
        val last = TimelineMath.latestPlayable(now)
        assertEquals(last - 50, TimelineMath.stepTarget(last - 150, 1, 100, now))
        assertNull(TimelineMath.stepTarget(last - 50, 1, 100, now))
        assertEquals(last - 250, TimelineMath.stepTarget(last - 150, -1, 100, now))
        assertEquals(last + 50, TimelineMath.stepTarget(last + 150, -1, 100, now)) // back is always allowed
    }

    @Test
    fun goToRefusesTimesThatAreNotRecordedYet() {
        assertNull(TimelineMath.goTo(now - 10_000, now, now))
        val w = TimelineMath.goTo(now - 3_600_000, now, now)!!
        assertEquals(now - 3_600_000, w.start)
        assertTrue(w.end <= now)
    }

    @Test
    fun quickRangesUseTheDayStartAndNow() {
        val midnight = now - now % 86_400_000L
        val (s, e) = TimelineMath.quickRange(TimelineMath.QuickRange.LastNight, now, midnight)
        assertEquals(midnight - 4 * 3_600_000L, s)
        assertEquals(midnight + 6 * 3_600_000L, e)
        val (ys, ye) = TimelineMath.quickRange(TimelineMath.QuickRange.ThisTimeYesterday, now, midnight)
        assertEquals(3_600_000L, ye - ys)
    }

    @Test
    fun timelineReplyIsReadAsStepsFromTheStart() {
        val json = Json.parseToJsonElement("""{"clips":[{"x1":0,"x2":10}],"alerts":[{"x1":4,"x2":6,"type":67108864}]}""")
        val t = Timeline.parse(json, now, 256)
        assertEquals(now, t.clips[0].startMs)
        assertEquals(2560L, t.clips[0].lengthMs)
        assertEquals(now + 1024, t.alerts[0].startMs)
        assertEquals(1L shl 26, t.alerts[0].flags)
    }
}
