package com.feerlessleadr.sightline.video

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A box around movement, as fractions 0..1 of the sampled picture, last seen at [t] (milliseconds). */
data class MotionBox(val x: Float, val y: Float, val w: Float, val h: Float, val t: Long)

/** A small greyscale picture (one byte per pixel, rows from the top) taken from a tile, 128 pixels wide. */
class GraySample(val pixels: ByteArray, val width: Int, val height: Int)

/**
 * How much a spot must change, and how big it must be, to get a box. Level 1 (only big, obvious movement) to 10
 * (almost any change); 3, 5 (the default) and 8 are the web app's old Low, Medium and High and the levels between
 * are blended.
 */
object MotionSensitivity {
    class Params(val floor: Float, val k: Float, val add: Float, val minCells: Int, val minArea: Float)

    private class Anchor(val level: Int, val floor: Float, val k: Float, val add: Float, val minCells: Float, val minArea: Float)

    private val anchors = listOf(
        Anchor(1, 30f, 4.2f, 14f, 6f, 0.012f),
        Anchor(3, 22f, 3.5f, 10f, 4f, 0.006f),
        Anchor(5, 14f, 3.0f, 6f, 2f, 0f),
        Anchor(8, 9f, 2.5f, 4f, 1f, 0f),
        Anchor(10, 6f, 2.1f, 3f, 1f, 0f),
    )

    fun params(level: Int): Params {
        val l = level.coerceIn(1, 10)
        var i = 0
        while (i < anchors.size - 2 && l > anchors[i + 1].level) i++
        val a = anchors[i]
        val b = anchors[i + 1]
        val t = (l - a.level).toFloat() / (b.level - a.level)
        fun mix(x: Float, y: Float) = x + (y - x) * t
        return Params(mix(a.floor, b.floor), mix(a.k, b.k), mix(a.add, b.add), mix(a.minCells, b.minCells).roundToInt(), mix(a.minArea, b.minArea))
    }

    fun describe(level: Int): String = when {
        level <= 2 -> "Only large, obvious movement. Ignores most leaves, shadows, rain and headlights."
        level <= 4 -> "Bigger, clearer movement. Fewer false boxes from leaves, shadows and rain."
        level == 5 -> "Balanced. A good starting point for most cameras."
        level <= 7 -> "Smaller movement too, like a pet farther from the camera."
        level <= 9 -> "Small or distant movement. Expect more boxes at night and in wind."
        else -> "Maximum. Almost any change gets a box, including noise and flicker."
    }
}

/**
 * Finds movement by comparing each picture of one tile with the one before (the web app's method): cells of 4 x 4
 * pixels that changed enough are joined into regions and each region becomes a box. A change of the whole picture
 * (lights, infrared switching, a camera move) is ignored. Boxes glide when movement continues and fade after
 * [LINGER_MS]. A new picture source starts over, and pictures in the first [SETTLE_MS] only become the reference.
 */
class MotionDetector(private val sensitivity: () -> Int) {
    private var boxes = mutableListOf<MotionBox>()
    private var prev: ByteArray? = null
    private var source = ""
    private var quietUntil = 0L

    /** Size of the picture the boxes' fractions refer to (for fitting them over the tile). */
    var pictureWidth = 0
        private set
    var pictureHeight = 0
        private set

    fun reset() {
        boxes = mutableListOf()
        prev = null
        source = ""
    }

    /** Boxes still worth drawing at [nowMs]. */
    fun visible(nowMs: Long): List<MotionBox> = boxes.filter { nowMs - it.t < LINGER_MS }

    /**
     * Compares [sample] with the previous one from the same [source] ("live", "own", "still"...). [pictureW] and
     * [pictureH] are the size of the whole picture the sample was taken from, if it is not the sample itself.
     */
    fun update(sample: GraySample, source: String, nowMs: Long, pictureW: Int = sample.width, pictureH: Int = sample.height) {
        pictureWidth = pictureW
        pictureHeight = pictureH
        if (this.source != source) {
            this.source = source
            prev = null
            quietUntil = nowMs + SETTLE_MS
        }
        val before = prev
        prev = sample.pixels
        boxes = boxes.filter { nowMs - it.t < LINGER_MS }.toMutableList()
        if (before != null && before.size == sample.pixels.size && nowMs >= quietUntil) {
            detect(before, sample.pixels, sample.width, sample.height, nowMs)
        }
    }

    private fun detect(a: ByteArray, b: ByteArray, sw: Int, sh: Int, nowMs: Long) {
        val gw = sw / CELL
        val gh = sh / CELL
        if (gw == 0 || gh == 0) return
        val diff = FloatArray(gw * gh)
        var total = 0f
        for (gy in 0 until gh) {
            for (gx in 0 until gw) {
                var s = 0
                for (y in gy * CELL until gy * CELL + CELL) {
                    val row = y * sw
                    for (x in gx * CELL until gx * CELL + CELL) s += abs((a[row + x].toInt() and 0xFF) - (b[row + x].toInt() and 0xFF))
                }
                val v = s.toFloat() / (CELL * CELL)
                diff[gy * gw + gx] = v
                total += v
            }
        }
        val mean = total / diff.size
        if (mean > 22f) return // the whole picture changed
        val p = MotionSensitivity.params(sensitivity())
        val thr = max(p.floor, mean * p.k + p.add)
        val on = BooleanArray(diff.size) { diff[it] > thr }
        val seen = BooleanArray(diff.size)
        val found = mutableListOf<MotionBox>()
        val stack = IntArray(diff.size)
        for (i in on.indices) {
            if (!on[i] || seen[i]) continue
            var x0 = gw
            var y0 = gh
            var x1 = 0
            var y1 = 0
            var n = 0
            var top = 0
            stack[top++] = i
            seen[i] = true
            while (top > 0) {
                val k = stack[--top]
                val x = k % gw
                val y = k / gw
                n++
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                for (dy in -2..2) for (dx in -2..2) { // bridge 1-cell gaps
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                    val q = ny * gw + nx
                    if (on[q] && !seen[q]) {
                        seen[q] = true
                        stack[top++] = q
                    }
                }
            }
            if (n < p.minCells) continue // tiny speckles are noise
            val bw = (x1 - x0 + 1).toFloat() / gw
            val bh = (y1 - y0 + 1).toFloat() / gh
            if (bw * bh < p.minArea) continue
            found += MotionBox(x0.toFloat() / gw, y0.toFloat() / gh, bw, bh, nowMs)
        }
        val list = boxes
        for (f in found) {
            // follow an existing box if it overlaps, so boxes glide instead of flickering
            val hit = list.indexOfFirst { f.x < it.x + it.w && it.x < f.x + f.w && f.y < it.y + it.h && it.y < f.y + f.h }
            if (hit >= 0) {
                val o = list[hit]
                list[hit] = MotionBox(o.x * 0.4f + f.x * 0.6f, o.y * 0.4f + f.y * 0.6f, o.w * 0.4f + f.w * 0.6f, o.h * 0.4f + f.h * 0.6f, nowMs)
            } else {
                list += f
            }
        }
        boxes = merge(list)
    }

    /** Joins boxes that touch or nearly touch (one moving object often shows up as a few edge fragments). */
    private fun merge(input: MutableList<MotionBox>): MutableList<MotionBox> {
        val list = input
        fun near(a: MotionBox, b: MotionBox): Boolean {
            val gx = max(a.x, b.x) - min(a.x + a.w, b.x + b.w)
            val gy = max(a.y, b.y) - min(a.y + a.h, b.y + b.h)
            return gx <= 0.05f && gy <= 0.07f
        }
        var changed = true
        while (changed) {
            changed = false
            loop@ for (i in list.indices) {
                for (j in i + 1 until list.size) {
                    val a = list[i]
                    val b = list[j]
                    if (!near(a, b)) continue
                    val x = min(a.x, b.x)
                    val y = min(a.y, b.y)
                    list[i] = MotionBox(x, y, max(a.x + a.w, b.x + b.w) - x, max(a.y + a.h, b.y + b.h) - y, max(a.t, b.t))
                    list.removeAt(j)
                    changed = true
                    break@loop
                }
            }
        }
        return list
    }

    companion object {
        private const val CELL = 4
        const val SETTLE_MS = 1500L
        const val LINGER_MS = 2200L
        const val SAMPLE_WIDTH = 128
    }
}

/** Shrinks RGBA pixels (rows from the top) to the greyscale sample the detector wants. */
fun grayOf(rgba: ByteArray, width: Int, height: Int): GraySample {
    val g = ByteArray(width * height)
    var j = 0
    for (i in g.indices) {
        val r = rgba[j].toInt() and 0xFF
        val gr = rgba[j + 1].toInt() and 0xFF
        val b = rgba[j + 2].toInt() and 0xFF
        g[i] = ((r * 77 + gr * 150 + b * 29) shr 8).toByte()
        j += 4
    }
    return GraySample(g, width, height)
}
