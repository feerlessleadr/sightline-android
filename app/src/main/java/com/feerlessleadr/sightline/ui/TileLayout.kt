package com.feerlessleadr.sightline.ui

/** How big the camera tiles are. Auto makes the whole group fit on the screen without scrolling. */
enum class TileSize(val label: String, val columns: Int?) {
    Auto("Fit screen", null),
    Large("Large", 1),
    Medium("Medium", 2),
    Small("Small", 3),
}

/** The shape one camera's tile takes in the grid, chosen by press-and-hold (the web app's Normal, Large 2x2, Wide 2x1, Tall 1x2). */
enum class TileShape(val label: String) {
    Normal("Normal"),
    Large("Large (2 x 2)"),
    Wide("Wide (2 x 1)"),
    Tall("Tall (1 x 2)"),
}

/** One group's hand-made arrangement: the order of its cameras and any non-normal shapes. */
data class GroupArrangement(
    val order: List<String> = emptyList(),
    val shapes: Map<String, TileShape> = emptyMap(),
) {
    val isDefault: Boolean get() = order.isEmpty() && shapes.isEmpty()

    /** [items] in the saved order. Cameras not in the saved order (new ones) keep their own order after the rest. */
    fun <T> sorted(items: List<T>, id: (T) -> String): List<T> {
        if (order.isEmpty()) return items
        val rank = order.withIndex().associate { it.value to it.index }
        val known = items.filter { id(it) in rank }.sortedBy { rank.getValue(id(it)) }
        return known + items.filter { id(it) !in rank }
    }

    /** The order after moving [id] to position [to] among [shown] (the ids as currently displayed). */
    fun moved(shown: List<String>, id: String, to: Int): GroupArrangement {
        val without = shown.filter { it != id }.toMutableList()
        without.add(to.coerceIn(0, without.size), id)
        return copy(order = without)
    }

    fun withShape(id: String, shape: TileShape): GroupArrangement =
        copy(shapes = if (shape == TileShape.Normal) shapes - id else shapes + (id to shape))

    fun encodeOrder(): String = order.joinToString(SEP)

    fun encodeShapes(): String = shapes.entries.joinToString(SEP) { it.key + PAIR + it.value.name }

    companion object {
        private const val SEP = "\u001f"
        private const val PAIR = "\u001e"

        fun decode(order: String?, shapes: String?): GroupArrangement {
            val ids = order?.split(SEP)?.filter { it.isNotEmpty() }.orEmpty()
            val map = shapes?.split(SEP)?.mapNotNull { pair ->
                val parts = pair.split(PAIR)
                val shape = parts.getOrNull(1)?.let { n -> TileShape.entries.firstOrNull { it.name == n } }
                if (parts.size == 2 && parts[0].isNotEmpty() && shape != null) parts[0] to shape else null
            }?.toMap().orEmpty()
            return GroupArrangement(ids, map)
        }
    }
}

class TileBox(val x: Float, val y: Float, val w: Float, val h: Float)

class TileGrid(val boxes: List<TileBox>, val contentHeight: Float, val columns: Int = 1)

private const val TILE_ASPECT = 16f / 9f

/**
 * Lays [count] 16:9 tiles into a [width] x [height] area with [gap] between them. [TileSize.Auto] picks the
 * column count that gives the biggest tiles while still fitting every tile on the screen (so four cameras
 * become 2 + 2, not 3 + 1) without any tile getting narrower than [minTileWidth]; if that cannot be done it
 * uses two columns and the grid scrolls.
 *
 * [shapes] gives each tile's shape (missing entries are Normal). Large and Wide tiles are full width when
 * [fullWidthBig] is set (phones); tiles are placed in order into the first free spot, like a dense grid.
 */
fun layoutTiles(
    count: Int,
    width: Float,
    height: Float,
    size: TileSize,
    gap: Float,
    minTileWidth: Float = 120f,
    shapes: List<TileShape> = emptyList(),
    fullWidthBig: Boolean = true,
): TileGrid {
    if (count <= 0 || width <= 0f) return TileGrid(emptyList(), 0f)
    val fixed = size.columns
    val columns = if (fixed != null) {
        minOf(fixed, count)
    } else {
        var best = 0
        var bestWidth = 0f
        for (c in 1..count) {
            val tileW = (width - gap * (c - 1)) / c
            val g = place(count, width, c, gap, shapes, fullWidthBig)
            if (g.contentHeight <= height && tileW >= minTileWidth && tileW > bestWidth) {
                best = c
                bestWidth = tileW
            }
        }
        if (best == 0) minOf(2, count) else best
    }
    return place(count, width, columns, gap, shapes, fullWidthBig)
}

private fun place(count: Int, width: Float, columns: Int, gap: Float, shapes: List<TileShape>, fullWidthBig: Boolean): TileGrid {
    val cellW = (width - gap * (columns - 1)) / columns
    val cellH = cellW / TILE_ASPECT
    val taken = ArrayList<BooleanArray>()
    fun row(r: Int): BooleanArray {
        while (taken.size <= r) taken.add(BooleanArray(columns))
        return taken[r]
    }
    val boxes = ArrayList<TileBox>(count)
    for (i in 0 until count) {
        val shape = shapes.getOrNull(i) ?: TileShape.Normal
        val big = if (fullWidthBig) columns else minOf(2, columns)
        val (cs, rs) = when (shape) {
            TileShape.Normal -> 1 to 1
            TileShape.Tall -> 1 to 2
            TileShape.Wide -> minOf(2, columns) to 1
            TileShape.Large -> big to big
        }
        var r = 0
        var placedAt: Pair<Int, Int>? = null
        while (placedAt == null) {
            for (c in 0..columns - cs) {
                var free = true
                for (dr in 0 until rs) for (dc in 0 until cs) if (row(r + dr)[c + dc]) free = false
                if (free) {
                    placedAt = r to c
                    break
                }
            }
            if (placedAt == null) r++
        }
        val (pr, pc) = placedAt
        for (dr in 0 until rs) for (dc in 0 until cs) row(pr + dr)[pc + dc] = true
        boxes += TileBox(pc * (cellW + gap), pr * (cellH + gap), cs * cellW + (cs - 1) * gap, rs * cellH + (rs - 1) * gap)
    }
    val rows = taken.size
    return TileGrid(boxes, rows * cellH + gap * (rows - 1), columns)
}
