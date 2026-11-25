package com.feerlessleadr.sightline.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementTest {
    @Test
    fun savedOrderIsKeptAndNewCamerasGoLast() {
        val a = GroupArrangement(order = listOf("c", "a"))
        assertEquals(listOf("c", "a", "b", "d"), a.sorted(listOf("a", "b", "c", "d")) { it })
    }

    @Test
    fun movingPutsTheCameraAtThePosition() {
        val a = GroupArrangement().moved(listOf("a", "b", "c", "d"), "a", 2)
        assertEquals(listOf("b", "c", "a", "d"), a.order)
    }

    @Test
    fun normalShapeIsNotStored() {
        val a = GroupArrangement().withShape("a", TileShape.Wide).withShape("b", TileShape.Tall).withShape("a", TileShape.Normal)
        assertEquals(mapOf("b" to TileShape.Tall), a.shapes)
    }

    @Test
    fun savesAndLoadsBack() {
        val a = GroupArrangement(listOf("x y", "z"), mapOf("x y" to TileShape.Large, "z" to TileShape.Tall))
        assertEquals(a, GroupArrangement.decode(a.encodeOrder(), a.encodeShapes()))
        assertTrue(GroupArrangement.decode(null, "garbage").isDefault)
    }

    @Test
    fun largeTileIsFullWidthOnAPhoneAndKeepsSixteenByNine() {
        val g = layoutTiles(3, 400f, 2000f, TileSize.Medium, 4f, shapes = listOf(TileShape.Large, TileShape.Normal, TileShape.Normal))
        val big = g.boxes[0]
        assertEquals(400f, big.w, 0.01f)
        // A big tile also spans the gap between cells, so it is up to one gap taller than exactly 16:9.
        assertEquals(big.w * 9f / 16f, big.h, 4f)
        // The two small ones sit side by side below the big one.
        assertTrue(g.boxes[1].y >= big.y + big.h)
        assertEquals(g.boxes[1].y, g.boxes[2].y, 0.01f)
    }

    @Test
    fun tallTileTakesTwoRowsAndOthersFillBesideIt() {
        val g = layoutTiles(3, 400f, 2000f, TileSize.Medium, 4f, shapes = listOf(TileShape.Tall, TileShape.Normal, TileShape.Normal))
        val tall = g.boxes[0]
        assertEquals(g.boxes[1].x, g.boxes[2].x, 0.01f)
        assertTrue(g.boxes[1].x > tall.x)
        assertTrue(g.boxes[2].y > g.boxes[1].y)
        assertEquals(g.boxes[1].h * 2 + 4f, tall.h, 0.01f)
    }
}
