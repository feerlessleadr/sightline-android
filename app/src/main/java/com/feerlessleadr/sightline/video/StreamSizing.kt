package com.feerlessleadr.sightline.video

import com.feerlessleadr.sightline.data.VideoQuality
import kotlin.math.roundToInt

/** One stream size and quality setting, the same table the web app uses (UI3's built-in profiles). */
data class StreamTier(val shortSide: Int, val width: Int, val height: Int, val quality: Int, val kbps: Int)

val StreamTiers = listOf(
    StreamTier(480, 856, 480, 30, 456),
    StreamTier(720, 1280, 720, 35, 1024),
    StreamTier(1080, 1920, 1080, 35, 2048),
    StreamTier(1440, 2560, 1440, 40, 4096),
    StreamTier(2160, 3840, 2160, 50, 8192),
)

/** What to ask Blue Iris for: picture size (multiples of 8), JPEG-style quality and bitrate. */
data class StreamRequest(val width: Int, val height: Int, val quality: Int, val kbps: Int)

private fun round8(n: Double) = maxOf(8, (n / 8).roundToInt() * 8)

/**
 * Size for the shared group stream: enough pixels for the grid's width (with a 25% margin), from the 720p tier
 * up to the 1440p tier, never larger than the group's own picture.
 */
fun groupStreamRequest(gridWidthPx: Float, groupWidth: Int, groupHeight: Int): StreamRequest {
    val need = gridWidthPx * 1.25f
    var i = StreamTiers.indexOfFirst { it.width >= need }
    if (i < 0 || i > 3) i = 3
    i = maxOf(1, i)
    val t = StreamTiers[i]
    val scale = minOf(1.0, t.width.toDouble() / groupWidth, t.height.toDouble() / groupHeight)
    return StreamRequest(round8(groupWidth * scale), round8(groupHeight * scale), t.quality, t.kbps)
}

/**
 * What the web app's "Auto" quality asks for in the one-camera player: the pixels the picture really covers (device
 * pixel ratio capped at 2) plus a quarter, rounded up to a tier, at least 720p and no bigger than the camera
 * needs. A phone held upright therefore gets the 720p tier (up to 1 Mbps). [viewWidth] and [viewHeight] are the
 * player area in density-independent pixels.
 */
fun playerStreamRequest(
    viewWidth: Float,
    viewHeight: Float,
    density: Float,
    cameraWidth: Int,
    cameraHeight: Int,
    quality: VideoQuality = VideoQuality.Auto,
): StreamRequest {
    val fixedSide = quality.shortSide
    val t = if (fixedSide != null) {
        StreamTiers.first { it.shortSide == fixedSide }
    } else {
        val ratio = minOf(2f, density)
        val scale = minOf(1.0, (viewWidth * ratio / cameraWidth).toDouble(), (viewHeight * ratio / cameraHeight).toDouble())
        val need = minOf(round8(cameraWidth * scale), round8(cameraHeight * scale))
        val camShort = minOf(cameraWidth, cameraHeight)
        var i = StreamTiers.indexOfFirst { it.shortSide >= need * 1.25 }
        if (i < 0) i = StreamTiers.size - 1
        i = maxOf(1, i)
        while (i > 0 && StreamTiers[i - 1].shortSide >= camShort) i--
        StreamTiers[i]
    }
    // Fit the camera's picture in the tier's box; the box turns to match a portrait camera.
    var boxW = t.width
    var boxH = t.height
    if ((boxW.toFloat() / boxH < 1) != (cameraWidth.toFloat() / cameraHeight < 1)) {
        val swap = boxW; boxW = boxH; boxH = swap
    }
    val fit = minOf(1.0, boxW.toDouble() / cameraWidth, boxH.toDouble() / cameraHeight)
    return StreamRequest(round8(cameraWidth * fit), round8(cameraHeight * fit), t.quality, t.kbps)
}

/**
 * Size for one camera's own stream, for a view [viewWidthPx] x [viewHeightPx] showing a camera whose picture is
 * [cameraWidth] x [cameraHeight]: the 720p to 1080p tier, never larger than the camera.
 */
fun cameraStreamRequest(viewWidthPx: Float, viewHeightPx: Float, cameraWidth: Int, cameraHeight: Int): StreamRequest {
    val need = maxOf(viewWidthPx, viewHeightPx * cameraWidth / cameraHeight)
    var i = StreamTiers.indexOfFirst { it.width >= need }
    if (i < 0 || i > 2) i = 2
    i = maxOf(1, i)
    val t = StreamTiers[i]
    val scale = minOf(
        1.0,
        t.width.toDouble() / cameraWidth,
        t.height.toDouble() / cameraHeight,
        maxOf(t.width, t.height).toDouble() / maxOf(cameraWidth, cameraHeight),
    )
    return StreamRequest(round8(cameraWidth * scale), round8(cameraHeight * scale), t.quality, t.kbps)
}

/**
 * Where each camera sits in a group's combined picture: [rects] are `[left, top, right, bottom]` boxes in a
 * picture of [width] x [height] pixels, one per name in [cams].
 */
data class GroupLayout(val cams: List<String>, val rects: List<List<Int>>, val width: Int, val height: Int) {
    /** The rectangle for [camera] (case-insensitive), as fractions 0..1 of the picture, or null if it is not in the group. */
    fun fractionFor(camera: String): FloatArray? {
        val i = cams.indexOfFirst { it.equals(camera, ignoreCase = true) }
        val r = rects.getOrNull(i) ?: return null
        if (width <= 0 || height <= 0) return null
        return floatArrayOf(r[0] / width.toFloat(), r[1] / height.toFloat(), r[2] / width.toFloat(), r[3] / height.toFloat())
    }

    /** The camera whose box holds the point ([fx], [fy]) given as fractions 0..1 of the group picture, or null. */
    fun cameraAt(fx: Float, fy: Float): String? {
        if (width <= 0 || height <= 0) return null
        val x = fx * width
        val y = fy * height
        val i = rects.indexOfFirst { x >= it[0] && x < it[2] && y >= it[1] && y < it[3] }
        return if (i >= 0) cams.getOrNull(i) else null
    }

    companion object {
        /**
         * Blue Iris describes the layout of each group stream in its `X-CamList` (a list of quoted names) and
         * `X-RecList` (a list of `[l,t,r,b]` boxes) headers, in the stream's own pixels. Returns null if absent or
         * inconsistent. The headers are Latin-1; non-ASCII names are re-decoded as UTF-8.
         */
        fun fromHeaders(camList: String?, recList: String?, width: Int, height: Int): GroupLayout? {
            if (camList.isNullOrBlank() || recList.isNullOrBlank() || width <= 0 || height <= 0) return null
            return try {
                val names = parseNames(camList)
                val boxes = parseBoxes(recList)
                if (names.isEmpty() || names.size != boxes.size) null else GroupLayout(names, boxes, width, height)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun parseNames(text: String): List<String> {
            val fixed = if (text.any { it.code > 0x7f }) String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) else text
            val out = ArrayList<String>()
            var i = 0
            while (i < fixed.length) {
                if (fixed[i] == '"') {
                    val sb = StringBuilder()
                    i++
                    while (i < fixed.length && fixed[i] != '"') {
                        if (fixed[i] == '\\' && i + 1 < fixed.length) i++
                        sb.append(fixed[i])
                        i++
                    }
                    out += sb.toString()
                }
                i++
            }
            return out
        }

        private fun parseBoxes(text: String): List<List<Int>> {
            val out = ArrayList<List<Int>>()
            Regex("""\[([^\[\]]*)]""").findAll(text).forEach { m ->
                val nums = m.groupValues[1].split(',').map { it.trim().toInt() }
                require(nums.size == 4)
                out += nums
            }
            return out
        }
    }
}

/** True if [data] (an Annex-B access unit) holds a key frame: H.264 IDR slice, or H.265 IRAP picture. */
fun isKeyFrame(data: ByteArray, hevc: Boolean): Boolean {
    var i = 0
    while (i + 3 < data.size) {
        if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 &&
            (data[i + 2].toInt() == 1 || (data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1))
        ) {
            val nal = if (data[i + 2].toInt() == 1) i + 3 else i + 4
            if (nal >= data.size) return false
            val b = data[nal].toInt() and 0xFF
            if (hevc) {
                if (((b shr 1) and 0x3F) in 16..21) return true
            } else if ((b and 0x1F) == 5) {
                return true
            }
            i = nal
        } else {
            i++
        }
    }
    return false
}
