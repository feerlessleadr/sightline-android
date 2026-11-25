package com.feerlessleadr.sightline.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupGridTest {
    // Four cameras of 16:9 in a 2 x 2 picture of 1280 x 720 (each 640 x 360), plus a fifth in the middle of nothing.
    private val layout = GroupLayout(
        cams = listOf("A", "B", "C", "D"),
        rects = listOf(listOf(0, 0, 640, 360), listOf(640, 0, 1280, 360), listOf(0, 360, 640, 720), listOf(640, 360, 1280, 720)),
        width = 1280,
        height = 720,
    )

    @Test
    fun onlyShownCamerasAreDrawnInTheLayoutsOrder() {
        val cells = GroupGrid.cells(layout, listOf("d", "A", "C"), 1000f, 1600f)
        assertEquals(listOf("A", "C", "D"), cells.map { it.camera })
    }

    @Test
    fun eachCellKeepsItsCamerasShapeAndCutsItsOwnPartOfThePicture() {
        val cells = GroupGrid.cells(layout, listOf("A", "B"), 1000f, 1600f)
        for (c in cells) assertEquals(16f / 9f, c.w / c.h, 0.01f)
        assertEquals(listOf(0f, 0f, 0.5f, 0.5f), cells[0].src.toList())
        assertEquals(listOf(0.5f, 0f, 1f, 0.5f), cells[1].src.toList())
    }

    @Test
    fun twoWideCamerasInATallStageStackAndFillTheWidth() {
        val cells = GroupGrid.cells(layout, listOf("A", "B"), 1000f, 1600f)
        assertEquals(1000f, cells[0].w, 1f)
        assertTrue(cells[1].y > cells[0].y + cells[0].h - 1f) // one above the other
        assertEquals(cells[0].x, cells[1].x, 1f)
    }

    @Test
    fun cellsStayInsideTheStageAndDoNotOverlap() {
        for (shown in listOf(listOf("A", "B", "C"), listOf("A", "B", "C", "D"), listOf("B"))) {
            val cells = GroupGrid.cells(layout, shown, 800f, 600f)
            for (c in cells) {
                assertTrue(c.x >= -0.5f && c.y >= -0.5f && c.x + c.w <= 800.5f && c.y + c.h <= 600.5f)
            }
            for (i in cells.indices) for (j in i + 1 until cells.size) {
                val a = cells[i]
                val b = cells[j]
                val overlap = a.x < b.x + b.w - 0.5f && b.x < a.x + a.w - 0.5f && a.y < b.y + b.h - 0.5f && b.y < a.y + a.h - 0.5f
                assertTrue("cells $i and $j overlap", !overlap)
            }
        }
    }

    @Test
    fun aShortLastRowIsCentred() {
        // Three cameras in two columns: the third is alone on its row, centred under the two.
        val cells = GroupGrid.cells(layout, listOf("A", "B", "C"), 800f, 600f)
        if (cells.size == 3 && cells[2].y > cells[0].y + 1f) {
            val middle = cells[2].x + cells[2].w / 2f
            assertEquals(400f, middle, 2f)
        }
    }

    @Test
    fun noShownCamerasMeansNoCells() {
        assertTrue(GroupGrid.cells(layout, emptyList(), 800f, 600f).isEmpty())
        assertTrue(GroupGrid.cells(layout, listOf("Z"), 800f, 600f).isEmpty())
    }

    @Test
    fun aTapFindsTheCellItIsIn() {
        val cells = GroupGrid.cells(layout, listOf("A", "B"), 1000f, 1600f)
        val first = cells[0]
        assertEquals("A", cells.firstOrNull { it.contains(first.x + 5f, first.y + 5f) }?.camera)
        assertNull(cells.firstOrNull { it.contains(-10f, -10f) })
    }
}
