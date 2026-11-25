package com.feerlessleadr.sightline.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

private const val SAMPLE_WIDTH = MotionDetector.SAMPLE_WIDTH
private const val SAMPLE_MAX_HEIGHT = 512

/**
 * One tile on screen: a box ([x], [y], [w], [h] in pixels from the view's top-left) that shows the part of the
 * decoded picture given by [src] (`[left, top, right, bottom]` as fractions 0..1 of the picture).
 * With [letterbox] the whole part is shown inside the box (portrait cameras); otherwise it fills the box,
 * trimming the edges.
 */
class TileSpec(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val src: FloatArray,
    val letterbox: Boolean = false,
    /** Names the tile in [GroupGlView.onSamples] (the camera id); empty = never sampled. */
    val id: String = "",
    /** Not drawn on screen, only sampled (a big tile showing its own stream still gets boxes from the shared picture). */
    val sampleOnly: Boolean = false,
)

/**
 * Draws many tiles from ONE decoded video picture, which is how a whole Blue Iris group is shown with a single
 * stream and a single decoder. [onSurface] gets the Surface to give MediaCodec (and null when it goes away).
 * Compose content placed above this view (names, dots, borders) shows over the video.
 */
class GroupGlView(context: Context) : GLSurfaceView(context) {
    private val renderer = TileRenderer()
    private val main = Handler(Looper.getMainLooper())

    /** Called on the main thread with a fresh decoder surface, or null when the surface is gone. */
    var onSurface: ((Surface?) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        renderer.onSurface = { surface -> main.post { onSurface?.invoke(surface) } }
    }

    /** Called on the main thread with a small greyscale picture of each tile that has an id, after [requestSamples]. */
    var onSamples: ((Map<String, GraySample>) -> Unit)? = null

    init {
        renderer.onSamples = { samples -> main.post { onSamples?.invoke(samples) } }
    }

    /** Asks for one greyscale sample of every identified tile on screen (used by the motion boxes). */
    fun requestSamples() {
        renderer.sampleRequested = true
        requestRender()
    }

    fun setTiles(tiles: List<TileSpec>) {
        renderer.tiles = tiles
        requestRender()
    }

    /** The size of the decoded picture, for cropping tiles to fill. Call when the stream header is read. */
    fun setVideoSize(width: Int, height: Int) {
        renderer.videoWidth = width
        renderer.videoHeight = height
        requestRender()
    }

    private class TileRenderer : Renderer {
        @Volatile var tiles: List<TileSpec> = emptyList()
        @Volatile var videoWidth = 0
        @Volatile var videoHeight = 0
        @Volatile var sampleRequested = false
        var onSurface: ((Surface?) -> Unit)? = null
        var onSamples: ((Map<String, GraySample>) -> Unit)? = null
        private var fbo = 0
        private var fboTexture = 0

        private var program = 0
        private var textureId = 0
        private var surfaceTexture: SurfaceTexture? = null
        private var surface: Surface? = null
        private var viewW = 1
        private var viewH = 1
        private val frameReady = java.util.concurrent.atomic.AtomicBoolean(false)
        private val texMatrix = FloatArray(16)
        private var aPos = 0
        private var aUv = 0
        private var uMatrix = 0
        private var uSrc = 0
        private val quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
            position(0)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            fbo = 0 // GL objects belong to the old context
            fboTexture = 0
            program = buildProgram()
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUv = GLES20.glGetAttribLocation(program, "aUv")
            uMatrix = GLES20.glGetUniformLocation(program, "uMatrix")
            uSrc = GLES20.glGetUniformLocation(program, "uSrc")
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            surface?.release()
            surfaceTexture?.release()
            val st = SurfaceTexture(textureId)
            st.setOnFrameAvailableListener { frameReady.set(true); requestRenderCallback?.invoke() }
            surfaceTexture = st
            val s = Surface(st)
            surface = s
            onSurface?.invoke(s)
        }

        var requestRenderCallback: (() -> Unit)? = null

        fun destroy() {
            surface?.release()
            surfaceTexture?.release()
            surface = null
            surfaceTexture = null
            onSurface?.invoke(null)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewW = width
            viewH = height
        }

        override fun onDrawFrame(gl: GL10?) {
            val st = surfaceTexture ?: return
            if (frameReady.getAndSet(false)) {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
            } else if (texMatrix[0] == 0f && texMatrix[5] == 0f) {
                android.opengl.Matrix.setIdentityM(texMatrix, 0)
            }
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            GLES20.glViewport(0, 0, viewW, viewH)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniformMatrix4fv(uMatrix, 1, false, texMatrix, 0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glEnableVertexAttribArray(aUv)
            for (tile in tiles) {
                if (tile.w <= 0 || tile.h <= 0 || tile.sampleOnly) continue
                if (tile.y + tile.h <= 0 || tile.y >= viewH || tile.x + tile.w <= 0 || tile.x >= viewW) continue
                drawTile(tile, viewH)
            }
            if (sampleRequested) {
                sampleRequested = false
                sampleTiles()
            }
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aUv)
        }

        /** Draws each identified tile again, small, into an off-screen picture and reads it back as greyscale. */
        private fun sampleTiles() {
            if (fbo == 0) {
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                fboTexture = ids[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexture)
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, SAMPLE_WIDTH, SAMPLE_MAX_HEIGHT, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glGenFramebuffers(1, ids, 0)
                fbo = ids[0]
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTexture, 0)
                // The picture texture must be the one bound for drawing again.
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
            val out = HashMap<String, GraySample>()
            for (tile in tiles) {
                if (tile.id.isEmpty() || tile.w <= 0 || tile.h <= 0) continue
                if (tile.y + tile.h <= 0 || tile.y >= viewH || tile.x + tile.w <= 0 || tile.x >= viewW) continue
                val sw = SAMPLE_WIDTH
                val sh = (SAMPLE_WIDTH * tile.h / tile.w.toFloat()).toInt().coerceIn(8, SAMPLE_MAX_HEIGHT)
                GLES20.glViewport(0, 0, sw, sh)
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
                GLES20.glScissor(0, 0, sw, sh)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                drawTile(TileSpec(0, 0, sw, sh, tile.src, tile.letterbox), sh)
                val buffer = ByteBuffer.allocateDirect(sw * sh * 4)
                GLES20.glReadPixels(0, 0, sw, sh, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
                val raw = ByteArray(sw * sh * 4)
                buffer.get(raw)
                // GL rows run bottom-up; the detector wants the top row first.
                val flipped = ByteArray(raw.size)
                for (row in 0 until sh) System.arraycopy(raw, (sh - 1 - row) * sw * 4, flipped, row * sw * 4, sw * 4)
                out[tile.id] = grayOf(flipped, sw, sh)
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            if (out.isNotEmpty()) onSamples?.invoke(out)
        }

        private fun drawTile(tile: TileSpec, targetHeight: Int) {
            var l = tile.src[0]
            var t = tile.src[1]
            var r = tile.src[2]
            var b = tile.src[3]
            var dx = tile.x
            var dy = tile.y
            var dw = tile.w
            var dh = tile.h
            val vw = videoWidth
            val vh = videoHeight
            if (vw > 0 && vh > 0) {
                val sw = (r - l) * vw
                val sh = (b - t) * vh
                if (sw >= 2f && sh >= 2f) {
                    if (tile.letterbox) {
                        val scale = min(tile.w / sw, tile.h / sh)
                        dw = (sw * scale).toInt()
                        dh = (sh * scale).toInt()
                        dx = tile.x + (tile.w - dw) / 2
                        dy = tile.y + (tile.h - dh) / 2
                    } else {
                        val aspect = tile.w.toFloat() / tile.h
                        if (sw / sh > aspect) {
                            val keep = sh * aspect / vw // fraction of the width to keep
                            val mid = (l + r) / 2
                            l = mid - keep / 2
                            r = mid + keep / 2
                        } else {
                            val keep = sw / aspect / vh
                            val mid = (t + b) / 2
                            t = mid - keep / 2
                            b = mid + keep / 2
                        }
                    }
                }
            }
            // GL's origin is the bottom-left; the picture fractions are measured from the top.
            val glY = targetHeight - dy - dh
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
            GLES20.glScissor(maxOf(0, dx), maxOf(0, glY), dw, dh)
            GLES20.glViewport(dx, glY, dw, dh)
            GLES20.glUniform4f(uSrc, l, 1f - b, r, 1f - t)
            quad.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
            quad.position(2)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun buildProgram(): Int {
            val vertex = """
                attribute vec2 aPos;
                attribute vec2 aUv;
                uniform mat4 uMatrix;
                uniform vec4 uSrc; // left, bottom, right, top of the wanted part, as fractions, bottom-up
                varying vec2 vUv;
                void main() {
                    gl_Position = vec4(aPos, 0.0, 1.0);
                    vec2 st = vec2(mix(uSrc.x, uSrc.z, aUv.x), mix(uSrc.y, uSrc.w, aUv.y));
                    vUv = (uMatrix * vec4(st, 0.0, 1.0)).xy;
                }
            """.trimIndent()
            val fragment = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vUv;
                uniform samplerExternalOES uTex;
                void main() { gl_FragColor = texture2D(uTex, vUv); }
            """.trimIndent()
            val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
            val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vs)
            GLES20.glAttachShader(p, fs)
            GLES20.glLinkProgram(p)
            return p
        }

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            return shader
        }
    }

    init {
        renderer.requestRenderCallback = { requestRender() }
    }

    /** Stops drawing and frees the decoder surface. Call when the view is going away. */
    fun release() {
        queueEvent { renderer.destroy() }
    }
}
