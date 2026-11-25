package com.feerlessleadr.sightline.video

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    @Test
    fun aWidePictureIsCentredInATallStage() {
        val fit = ZoomMath.fit(1000f, 1600f, 16f / 9f)
        assertEquals(0f, fit[0], 0.01f)
        assertEquals(1000f, fit[2], 0.01f)
        assertEquals(562.5f, fit[3], 0.01f)
        assertEquals((1600f - 562.5f) / 2f, fit[1], 0.01f)
    }

    @Test
    fun fillingCropsTheEdgesInsteadOfLeavingBars() {
        val fit = ZoomMath.fit(1000f, 1600f, 16f / 9f, cover = true)
        // The picture is as tall as the stage and wider than it: 1600 high, 2844.4 wide, centred (negative left).
        assertEquals(1600f, fit[3], 0.01f)
        assertEquals(1600f * 16f / 9f, fit[2], 0.1f)
        assertEquals((1000f - fit[2]) / 2f, fit[0], 0.01f)
        assertEquals(0f, fit[1], 0.01f)
        // At no zoom the offset is still 0, and panning cannot leave a gap at either side.
        assertEquals(0f, ZoomMath.clampAxis(fit[0], fit[2], 1000f, 1f, 0f), 0.01f)
    }

    @Test
    fun noZoomMeansNoOffset() {
        val fit = ZoomMath.fit(1000f, 1600f, 16f / 9f)
        assertEquals(0f, ZoomMath.clampAxis(fit[0], fit[2], 1000f, 1f, 0f), 0.01f)
        assertEquals(0f, ZoomMath.clampAxis(fit[1], fit[3], 1600f, 1f, 0f), 0.01f)
    }

    @Test
    fun zoomedPictureGrowsIntoTheFreeSpaceAboveAndBelow() {
        val fit = ZoomMath.fit(1000f, 1600f, 16f / 9f)
        // At 2x the picture is 1125 high, still less than the 1600 stage: it stays centred vertically.
        val ty = ZoomMath.clampAxis(fit[1], fit[3], 1600f, 2f, 300f)
        val top = fit[1] * 2f + ty
        assertEquals((1600f - 1125f) / 2f, top, 0.01f)
        // Horizontally it is wider than the stage, so it cannot leave a gap: the offset is limited to [-1000, 0].
        assertEquals(0f, ZoomMath.clampAxis(fit[0], fit[2], 1000f, 2f, 500f), 0.01f)
        assertEquals(-1000f, ZoomMath.clampAxis(fit[0], fit[2], 1000f, 2f, -5000f), 0.01f)
    }

    @Test
    fun aBadOffsetNeverComesOut() {
        val fit = ZoomMath.fit(1000f, 1600f, 16f / 9f)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val x = ZoomMath.clampAxis(fit[0], fit[2], 1000f, 3f, bad)
            val y = ZoomMath.clampAxis(fit[1], fit[3], 1600f, 3f, bad)
            assertEquals(false, x.isNaN() || x.isInfinite())
            assertEquals(false, y.isNaN() || y.isInfinite())
        }
    }

    @Test
    fun onceTheZoomedPictureIsTallerThanTheStageItCannotLeaveAGap() {
        val fit = ZoomMath.fit(1000f, 1600f, 16f / 9f)
        val scale = 4f // 2250 high
        val low = ZoomMath.clampAxis(fit[1], fit[3], 1600f, scale, -9999f)
        val high = ZoomMath.clampAxis(fit[1], fit[3], 1600f, scale, 9999f)
        assertEquals(1600f, fit[1] * scale + low + fit[3] * scale, 0.01f) // bottom edge on the stage bottom
        assertEquals(0f, fit[1] * scale + high, 0.01f) // top edge on the stage top
    }
}
