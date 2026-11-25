package com.feerlessleadr.sightline.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.feerlessleadr.sightline.data.BlueIrisException
import com.feerlessleadr.sightline.data.VideoStream
import com.feerlessleadr.sightline.video.StreamRequest
import com.feerlessleadr.sightline.video.playLive
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NO_PICTURE_MS = 10_000L

/**
 * A big tile's own live stream of one camera, drawn by a TextureView that sits inside the tile (the shared group
 * picture is only a cut-out of the whole group, so a tile shown large would just be that piece enlarged). It fills
 * the tile like the stills do; a portrait camera is shown whole instead. [onShowing] says whether a picture is
 * being shown, [onFailed] is called when the stream could not start, stalled for 10 s or ended.
 */
@Composable
fun OwnVideo(
    cameraId: String,
    request: StreamRequest,
    portrait: Boolean,
    openVideo: suspend (id: String, size: StreamRequest?) -> VideoStream,
    onShowing: (Boolean) -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val showing by rememberUpdatedState(onShowing)
    val failed by rememberUpdatedState(onFailed)
    var surface by remember { mutableStateOf<Surface?>(null) }
    var videoSize by remember { mutableStateOf(0 to 0) }

    LaunchedEffect(surface, cameraId, request) {
        val s = surface ?: return@LaunchedEffect
        try {
            var picture = false
            coroutineScope {
                val stream = openVideo(cameraId, request)
                val player = launch {
                    playLive(
                        stream, s,
                        onSize = { w, h -> videoSize = w to h },
                        onStats = { st -> if (st.shown > 0 && !picture) { picture = true; showing(true) } },
                    )
                }
                val watchdog = launch {
                    delay(NO_PICTURE_MS)
                    if (!picture) player.cancel()
                }
                player.join()
                watchdog.cancel()
            }
            showing(false)
            failed()
        } catch (e: CancellationException) {
            showing(false)
            throw e
        } catch (e: BlueIrisException) {
            showing(false)
            failed()
        } catch (e: RuntimeException) {
            showing(false)
            failed()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).also { tv ->
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        surface = Surface(st)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
                if (tv.isAvailable) surface = Surface(tv.surfaceTexture!!)
            }
        },
        update = { tv ->
            val (w, h) = videoSize
            tv.post { fitVideo(tv, w, h, portrait) }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            showing(false) // the tile goes back to the shared picture until a new own stream shows
            surface?.release()
            surface = null
        }
    }
}

/** Scales the stretched texture so the picture keeps its shape: filling the view (cropping) or, if [portrait], fitting inside it. */
private fun fitVideo(tv: TextureView, videoW: Int, videoH: Int, portrait: Boolean) {
    val vw = tv.width.toFloat()
    val vh = tv.height.toFloat()
    if (videoW <= 0 || videoH <= 0 || vw <= 0f || vh <= 0f) return
    val s = if (portrait) min(vw / videoW, vh / videoH) else max(vw / videoW, vh / videoH)
    val m = Matrix()
    m.setScale(videoW * s / vw, videoH * s / vh, vw / 2f, vh / 2f)
    tv.setTransform(m)
}
