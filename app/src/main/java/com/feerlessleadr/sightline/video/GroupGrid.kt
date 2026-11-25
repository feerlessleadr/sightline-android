package com.feerlessleadr.sightline.video

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * One shown camera in the grid that replaces Blue Iris's group picture: where it is drawn ([x], [y], [w], [h], in
 * pixels of the stage) and which part of the group picture it shows ([src]: left, top, right, bottom as fractions 0..1).
 */
class GridCell(val camera: String, val x: Float, val y: Float, val w: Float, val h: Float, val src: FloatArray) {
    fun contains(px: Float, py: Float) = px >= x && px < x + w && py >= y && py < y + h
}

/**
 * Blue Iris's group picture contains every camera of the group, even ones the app does not show (hidden in Blue Iris,
 * disabled, or not allowed). When there are such cameras the app draws only the shown ones, each cut out of the group
 * picture, into a grid of its own that fits the screen (the web app's `rgLayout`).
 */
object GroupGrid {
    /**
     * The cells for the cameras of [layout] named in [shown] (case-insensitive), in the layout's order, packed into a
     * [width] x [height] stage with [gap] between them. Every cell keeps its camera's own shape; the number of columns
     * is the one that shows the cameras biggest; a short last row is centred.
     */
    fun cells(layout: GroupLayout, shown: Collection<String>, width: Float, height: Float, gap: Float = 2f): List<GridCell> {
        val wanted = shown.map { it.lowercase() }.toSet()
        val idx = layout.cams.indices.filter { layout.cams[it].lowercase() in wanted && layout.rects.getOrNull(it) != null }
        val n = idx.size
        if (n == 0 || width <= 0f || height <= 0f || layout.width <= 0 || layout.height <= 0) return emptyList()
        fun aspect(i: Int): Float {
            val r = layout.rects[i]
            return (r[2] - r[0]).toFloat() / max(1, r[3] - r[1])
        }
        // A typical camera's shape (the middle one) sets the cell shape.
        val a = idx.map { aspect(it) }.sorted()[n / 2].takeIf { it > 0f } ?: (16f / 9f)
        var best: List<GridCell>? = null
        var bestArea = -1f
        for (cols in 1..n) {
            val rows = ceil(n / cols.toDouble()).toInt()
            val bw = min((width - gap * (cols - 1)) / cols, (height - gap * (rows - 1)) / rows * a)
            val bh = bw / a
            val y0 = (height - rows * bh - gap * (rows - 1)) / 2f
            var area = 0f
            val cells = idx.mapIndexed { k, i ->
                val ai = aspect(i)
                val scale = min(bw / ai, bh)
                val w = scale * ai
                val h = scale
                val row = k / cols
                val inRow = min(cols, n - row * cols)
                val x0 = (width - inRow * bw - gap * (inRow - 1)) / 2f
                area += w * h
                val r = layout.rects[i]
                GridCell(
                    camera = layout.cams[i],
                    x = x0 + (k % cols) * (bw + gap) + (bw - w) / 2f,
                    y = y0 + row * (bh + gap) + (bh - h) / 2f,
                    w = w,
                    h = h,
                    src = floatArrayOf(
                        r[0] / layout.width.toFloat(), r[1] / layout.height.toFloat(),
                        r[2] / layout.width.toFloat(), r[3] / layout.height.toFloat(),
                    ),
                )
            }
            if (area > bestArea + 1f) {
                bestArea = area
                best = cells
            }
        }
        return best.orEmpty()
    }
}
