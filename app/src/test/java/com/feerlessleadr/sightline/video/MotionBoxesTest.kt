package com.feerlessleadr.sightline.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionBoxesTest {
    private val w = 128
    private val h = 72

    private fun flat(value: Int) = GraySample(ByteArray(w * h) { value.toByte() }, w, h)

    /** A grey picture with a brighter square at ([x], [y]) of [size] pixels. */
    private fun withSquare(x: Int, y: Int, size: Int, bright: Int = 200): GraySample {
        val px = ByteArray(w * h) { 60 }
        for (yy in y until y + size) for (xx in x until x + size) px[yy * w + xx] = bright.toByte()
        return GraySample(px, w, h)
    }

    @Test
    fun levelFiveUsesTheWebAppsMediumNumbers() {
        val p = MotionSensitivity.params(5)
        assertEquals(14f, p.floor, 0.001f)
        assertEquals(3f, p.k, 0.001f)
        assertEquals(6f, p.add, 0.001f)
        assertEquals(2, p.minCells)
    }

    @Test
    fun levelsBetweenAnchorsAreBlended() {
        val p = MotionSensitivity.params(4)
        assertEquals(18f, p.floor, 0.001f) // halfway between 22 (level 3) and 14 (level 5)
    }

    @Test
    fun nothingMovingGivesNoBoxes() {
        val d = MotionDetector { 5 }
        d.update(flat(60), "still", 0)
        d.update(flat(60), "still", 2_000)
        d.update(flat(60), "still", 2_600)
        assertTrue(d.visible(2_600).isEmpty())
    }

    @Test
    fun movementGetsABoxAroundIt() {
        val d = MotionDetector { 5 }
        d.update(withSquare(10, 10, 8), "still", 0)
        d.update(withSquare(10, 10, 8), "still", 2_000)
        d.update(withSquare(80, 40, 16), "still", 2_600)
        val boxes = d.visible(2_600)
        assertTrue(boxes.isNotEmpty())
        // Something changed at the new square, so a box sits around x = 80..96 of 128 and y = 40..56 of 72.
        assertTrue(boxes.any { it.x < 96f / w && it.x + it.w > 80f / w && it.y < 56f / h && it.y + it.h > 40f / h })
    }

    @Test
    fun pictureAfterANewSourceOnlyBecomesTheReference() {
        val d = MotionDetector { 5 }
        d.update(flat(60), "live", 0)
        d.update(withSquare(40, 20, 20), "live", 500) // inside the 1.5 s settling time: ignored
        assertTrue(d.visible(500).isEmpty())
    }

    @Test
    fun aChangeOfTheWholePictureIsIgnored() {
        val d = MotionDetector { 10 }
        d.update(flat(40), "still", 0)
        d.update(flat(40), "still", 2_000)
        d.update(flat(120), "still", 2_600) // lights switched on
        assertTrue(d.visible(2_600).isEmpty())
    }

    @Test
    fun lowSensitivityIgnoresASmallChangeThatHighSensitivityBoxes() {
        fun run(level: Int): Int {
            val d = MotionDetector { level }
            d.update(withSquare(10, 10, 4, 60), "still", 0)
            d.update(withSquare(10, 10, 4, 60), "still", 2_000)
            d.update(withSquare(10, 10, 4, 100), "still", 2_600)
            return d.visible(2_600).size
        }
        assertEquals(0, run(1))
        assertTrue(run(10) > 0)
    }

    @Test
    fun thePlayerShowsBoxesForLessTimeThanTheGrid() {
        val d = MotionDetector { 5 }
        d.update(withSquare(10, 10, 8), "still", 0)
        d.update(withSquare(10, 10, 8), "still", 2_000)
        d.update(withSquare(80, 40, 16), "still", 2_600)
        assertTrue(d.visible(2_600 + 800, 900L).isNotEmpty())
        assertTrue(d.visible(2_600 + 1_000, 900L).isEmpty())
        assertTrue(d.visible(2_600 + 1_000).isNotEmpty()) // the grid's 2.2 s
    }

    @Test
    fun boxesFadeAway() {
        val d = MotionDetector { 5 }
        d.update(withSquare(10, 10, 8), "still", 0)
        d.update(withSquare(10, 10, 8), "still", 2_000)
        d.update(withSquare(80, 40, 16), "still", 2_600)
        assertTrue(d.visible(2_600 + MotionDetector.LINGER_MS + 1).isEmpty())
    }
}
