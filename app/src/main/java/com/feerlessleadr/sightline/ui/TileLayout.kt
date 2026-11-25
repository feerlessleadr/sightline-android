package com.feerlessleadr.sightline.ui

import kotlin.math.ceil

/** How big the camera tiles are. Auto makes the whole group fit on the screen without scrolling. */
enum class TileSize(val label: String, val columns: Int?) {
    Auto("Fit screen", null),
    Large("Large", 1),
    Medium("Medium", 2),
    Small("Small", 3),
}

class TileBox(val x: Float, val y: Float, val w: Float, val h: Float)

class TileGrid(val boxes: List<TileBox>, val contentHeight: Float)

private const val TILE_ASPECT = 16f / 9f

/**
 * Lays [count] 16:9 tiles into a [width] x [height] area with [gap] between them. [TileSize.Auto] picks the
 * column count that gives the biggest tiles while still fitting every tile on the screen (so four cameras
 * become 2 + 2, not 3 + 1) without any tile getting narrower than [minTileWidth]; if that cannot be done it
 * uses two columns and the grid scrolls.
 */
fun layoutTiles(count: Int, width: Float, height: Float, size: TileSize, gap: Float, minTileWidth: Float = 120f): TileGrid {
    if (count <= 0 || width <= 0f) return TileGrid(emptyList(), 0f)
    val fixed = size.columns
    val columns = if (fixed != null) {
        minOf(fixed, count)
    } else {
        var best = 0
        var bestWidth = 0f
        for (c in 1..count) {
            val tileW = (width - gap * (c - 1)) / c
            val rows = ceil(count / c.toDouble()).toInt()
            val total = rows * (tileW / TILE_ASPECT) + gap * (rows - 1)
            if (total <= height && tileW >= minTileWidth && tileW > bestWidth) {
                best = c
                bestWidth = tileW
            }
        }
        if (best == 0) minOf(2, count) else best
    }
    val tileW = (width - gap * (columns - 1)) / columns
    val tileH = tileW / TILE_ASPECT
    val boxes = List(count) { i ->
        TileBox((i % columns) * (tileW + gap), (i / columns) * (tileH + gap), tileW, tileH)
    }
    val rows = ceil(count / columns.toDouble()).toInt()
    return TileGrid(boxes, rows * tileH + gap * (rows - 1))
}
