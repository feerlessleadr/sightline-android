package com.feerlessleadr.sightline.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileLayoutTest {
    @Test fun `four cameras on a phone make 2 plus 2, not 3 plus 1`() {
        val grid = layoutTiles(4, width = 400f, height = 700f, size = TileSize.Auto, gap = 4f)
        assertEquals(2f, grid.boxes.map { it.y }.distinct().size.toFloat(), 0f) // two rows
        assertEquals(2, grid.boxes.map { it.x }.distinct().size) // two columns
        assertTrue(grid.contentHeight <= 700f)
    }

    @Test fun `one camera fills the width`() {
        val grid = layoutTiles(1, 400f, 700f, TileSize.Auto, 4f)
        assertEquals(400f, grid.boxes.single().w, 0.01f)
    }

    @Test fun `fixed sizes use their column counts and may scroll`() {
        val large = layoutTiles(5, 400f, 300f, TileSize.Large, 4f)
        assertEquals(1, large.boxes.map { it.x }.distinct().size)
        assertTrue(large.contentHeight > 300f)
        val small = layoutTiles(7, 400f, 300f, TileSize.Small, 4f)
        assertEquals(3, small.boxes.map { it.x }.distinct().size)
        assertEquals(3, small.boxes.map { it.y }.distinct().size)
    }

    @Test fun `too many cameras for the screen fall back to two columns and scroll`() {
        val grid = layoutTiles(40, 400f, 600f, TileSize.Auto, 4f, minTileWidth = 100f)
        assertEquals(2, grid.boxes.map { it.x }.distinct().size)
        assertTrue(grid.contentHeight > 600f)
    }

    @Test fun `nothing to lay out gives an empty grid`() {
        assertTrue(layoutTiles(0, 400f, 700f, TileSize.Auto, 4f).boxes.isEmpty())
    }
}
