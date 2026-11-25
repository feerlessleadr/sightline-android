package com.feerlessleadr.sightline.video

import kotlin.math.max
import kotlin.math.min

/**
 * The arithmetic of digital zoom on the player's stage. A point of the stage is drawn at `point * scale + offset`
 * (the same on both axes), so the picture can be fitted in the stage first and then zoomed and panned.
 */
object ZoomMath {
    const val MAX = 8f

    /**
     * Where a picture of [aspect] (width / height) sits in a [stageW] x [stageH] stage, centred: left, top, width, height.
     * Normally the whole picture fits inside; with [cover] it fills the stage and the edges that do not fit are cropped
     * (the rectangle is then bigger than the stage, so left or top is negative).
     */
    fun fit(stageW: Float, stageH: Float, aspect: Float, cover: Boolean = false): FloatArray {
        if (stageW <= 0f || stageH <= 0f || aspect <= 0f) return floatArrayOf(0f, 0f, stageW, stageH)
        val w = if (cover) max(stageW, stageH * aspect) else min(stageW, stageH * aspect)
        val h = w / aspect
        return floatArrayOf((stageW - w) / 2f, (stageH - h) / 2f, w, h)
    }

    /**
     * Keeps the zoomed picture inside the stage along one axis. The picture spans [lo] to [lo] + [len] in a stage of
     * length [full]; [wanted] is the wanted offset after zooming by [scale]. If the zoomed picture is smaller than the
     * stage it stays centred (so it grows into the free space); otherwise it may not leave a gap at either edge.
     */
    fun clampAxis(lo: Float, len: Float, full: Float, scale: Float, wanted: Float): Float {
        // A bad number (not a number, or infinite) must never reach the transform: it draws nothing.
        val t = if (wanted.isNaN() || wanted.isInfinite()) 0f else wanted
        val a = lo * scale
        val l = len * scale
        return if (l <= full) (full - l) / 2f - a else min(-a, max(full - a - l, t))
    }
}
