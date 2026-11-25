package com.feerlessleadr.sightline.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.feerlessleadr.sightline.video.BlueStreamReader
import com.feerlessleadr.sightline.video.StreamBlock
import com.feerlessleadr.sightline.video.StreamRequest
import okio.BufferedSource

/** Something went wrong talking to Blue Iris. The message is safe to show to the user. */
open class BlueIrisException(message: String) : Exception(message)

/** Wrong user name or password, or Blue Iris refused the sign-in. */
class LoginFailedException(message: String) : BlueIrisException(message)

/** The session ended; sign in again. */
class SessionExpiredException : BlueIrisException("Your session expired. Please sign in again.")

/** The signed-in account is not allowed to do this (for example the system log for a non-admin). */
class AccessDeniedException(message: String) : BlueIrisException(message)

/** An open live-video response. [source] streams the Blue Iris container; closing it ends the connection. */
class VideoStream internal constructor(private val response: Response) : java.io.Closeable {
    val source: BufferedSource = response.body.source()

    /** For a group stream, Blue Iris names the cameras in the picture and where each sits in these two headers. */
    val camListHeader: String? = response.header("X-CamList")
    val recListHeader: String? = response.header("X-RecList")
    override fun close() = response.close()
}

/** Sends the Blue Iris session as a cookie for this one host, so the token never goes into a URL. */
internal class SessionCookieJar(private val host: String) : CookieJar {
    @Volatile
    var session: String? = null

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val token = session ?: return emptyList()
        if (url.host != host) return emptyList()
        return listOf(Cookie.Builder().name("session").value(token).domain(host).path("/").build())
    }

    // Blue Iris's own Set-Cookie headers are ignored on purpose.
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
}

/**
 * Talks to one Blue Iris server. [baseUrl] must come from [ServerAddress.parse] (HTTPS only).
 * Redirects are never followed, replies are size-limited, and the password is never kept.
 */
class BlueIrisClient(
    private val baseUrl: HttpUrl,
    httpClient: OkHttpClient = OkHttpClient(),
) {
    private val cookies = SessionCookieJar(baseUrl.host)
    private val http = httpClient.newBuilder()
        .cookieJar(cookies)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var session: String? = null

    /** The current session token, for the encrypted session store. Never log it or put it in a URL. */
    val sessionToken: String? get() = session

    /** Resumes a session saved earlier. The server decides whether it is still valid on the next call. */
    fun restore(token: String) {
        session = token
        cookies.session = token
    }

    /** Blue Iris's challenge/response sign-in. The password is only used to compute the hash. */
    suspend fun login(user: String, password: String): LoginInfo {
        val challenge = call(buildJsonObject { put("cmd", "login") }, authed = false, checkResult = false)
        val token = challenge.session ?: throw BlueIrisException("Unexpected reply from Blue Iris.")
        if (challenge.result == "success") return finishLogin(token, challenge)
        val reply = call(
            buildJsonObject {
                put("cmd", "login")
                put("session", token)
                put("response", md5Hex("$user:$token:$password"))
            },
            authed = false,
            checkResult = false,
        )
        if (reply.result != "success") {
            throw LoginFailedException(reasonOf(reply) ?: "Sign-in failed.")
        }
        return finishLogin(reply.session ?: token, reply)
    }

    private fun finishLogin(token: String, envelope: Envelope): LoginInfo {
        session = token
        cookies.session = token
        val d = envelope.data as? JsonObject
        fun flag(name: String) = d?.get(name)?.jsonPrimitive?.booleanOrNull ?: false
        return LoginInfo(
            systemName = d?.get("system name")?.jsonPrimitive?.contentOrNull,
            version = d?.get("version")?.jsonPrimitive?.contentOrNull,
            user = d?.get("user")?.jsonPrimitive?.contentOrNull,
            admin = flag("admin"),
            canPtz = flag("ptz"),
            canExportClips = flag("clipcreate"),
        )
    }

    suspend fun cameras(): CameraList {
        val reply = call(buildJsonObject { put("cmd", "camlist") })
        return CameraList.parse(reply.data)
    }

    /** Alerts from the last [seconds] seconds on every camera (what the web app polls every 4 s for AI labels). */
    suspend fun recentAlerts(seconds: Int = 120): List<RecentAlert> {
        val now = System.currentTimeMillis() / 1000
        val reply = call(
            buildJsonObject {
                put("cmd", "alertlist")
                put("camera", "index")
                put("startdate", now - seconds)
                put("enddate", now + 60)
                put("view", "alerts")
            },
        )
        val array = reply.data as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val camera = o["camera"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            RecentAlert(
                camera = camera,
                memo = o["memo"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                dateSeconds = o["date"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L,
                flags = o["flags"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L,
            )
        }
    }

    /** Keeps the Blue Iris session from idling out (the web app does this every 40 s). */
    suspend fun keepAlive() {
        call(buildJsonObject { put("cmd", "status") })
    }

    /**
     * A current JPEG still of [camera] (a camera short name, or a group). Uses the session cookie only,
     * so the token is never in the URL. [width] is clamped to a sane range.
     */
    suspend fun still(camera: String, width: Int = 480, quality: Int = 60): ByteArray {
        if (session == null) throw SessionExpiredException()
        val url = baseUrl.newBuilder()
            .addPathSegment("image")
            .addPathSegment(camera)
            .addQueryParameter("w", width.coerceIn(80, 1920).toString())
            .addQueryParameter("q", quality.coerceIn(10, 100).toString())
            .build()
        val request = Request.Builder().url(url).get().build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    if (response.isRedirect) throw SessionExpiredException()
                    if (response.code == 401 || response.code == 403) throw SessionExpiredException()
                    if (!response.isSuccessful) throw BlueIrisException("Blue Iris answered HTTP ${response.code}.")
                    val type = response.header("Content-Type").orEmpty()
                    if (!type.startsWith("image/", ignoreCase = true)) throw BlueIrisException("Blue Iris did not send a picture.")
                    val source = response.body.source()
                    source.request(MAX_IMAGE_BYTES + 1)
                    if (source.buffer.size > MAX_IMAGE_BYTES) throw BlueIrisException("Blue Iris sent a picture that is too large.")
                    source.readByteArray()
                }
            } catch (e: IOException) {
                throw BlueIrisException("Can't reach Blue Iris.")
            }
        }
    }

    /**
     * Opens an alert's recording file (`/file/clips/`) from [offsetMs] into it, played at [speedPercent]. Same container as
     * live video; the caller falls back to the timeline if Blue Iris refuses.
     */
    suspend fun openClip(file: String, offsetMs: Long, speedPercent: Int, size: StreamRequest? = null, audio: Boolean = false): VideoStream =
        openStream(
            listOf("file", "clips", file), size,
            listOf("speed" to speedPercent.coerceIn(10, 1600).toString(), "time" to offsetMs.coerceAtLeast(0).toString()),
            audio,
        )

    /**
     * One frame of the recording of [camera] at [positionMs] as a JPEG (what the web app shows while a recording is
     * paused or being scrubbed), or a [BlueIrisException] if Blue Iris has nothing at that time.
     */
    suspend fun recordedStill(camera: String, positionMs: Long, width: Int = 960): ByteArray {
        if (session == null) throw SessionExpiredException()
        val url = baseUrl.newBuilder()
            .addPathSegment("time")
            .addPathSegment(camera)
            .addQueryParameter("jpeg", null)
            .addQueryParameter("isolate", null)
            .addQueryParameter("pos", positionMs.toString())
            .addQueryParameter("w", width.coerceIn(80, 1920).toString())
            .addQueryParameter("q", "70")
            .build()
        val request = Request.Builder().url(url).get().build()
        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    if (response.isRedirect || response.code == 401 || response.code == 403) throw SessionExpiredException()
                    if (!response.isSuccessful) throw BlueIrisException("Blue Iris answered HTTP ${response.code}.")
                    val type = response.header("Content-Type").orEmpty()
                    if (!type.startsWith("image/", ignoreCase = true)) throw BlueIrisException("Blue Iris has no picture for that time.")
                    val source = response.body.source()
                    source.request(MAX_IMAGE_BYTES + 1)
                    if (source.buffer.size > MAX_IMAGE_BYTES) throw BlueIrisException("Blue Iris sent a picture that is too large.")
                    source.readByteArray()
                }
            } catch (e: IOException) {
                throw BlueIrisException("Can't reach Blue Iris.")
            }
        }
    }

    /**
     * Where [camera] has recordings and alerts between [startMs] and [endMs] (milliseconds since 1970), at about
     * [msecPerPixel] milliseconds per step. The reply gives spans as step numbers from the start.
     */
    suspend fun timeline(camera: String, startMs: Long, endMs: Long, msecPerPixel: Long): Timeline {
        val startSec = startMs / 1000
        val reply = call(
            buildJsonObject {
                put("cmd", "timeline")
                put("startdate", startSec)
                put("enddate", (endMs + 999) / 1000)
                put("msecpp", msecPerPixel)
                put("camera", camera)
            },
        )
        return Timeline.parse(reply.data, startSec * 1000, msecPerPixel)
    }

    /** The alerts of one camera in a period (for jumping to the previous and next one), oldest first. */
    suspend fun cameraAlerts(camera: String, startMs: Long, endMs: Long): List<AlertItem> {
        val reply = call(
            buildJsonObject {
                put("cmd", "alertlist")
                put("camera", camera)
                put("startdate", startMs / 1000)
                put("enddate", endMs / 1000)
                put("view", "alerts")
            },
        )
        val array = reply.data as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.mapNotNull { AlertItem.parse(it) }.sortedBy { it.timeMs }
    }

    /**
     * Opens the live video stream of [camera]. The caller reads [VideoStream.source] and must close it.
     * Cookie only, HTTPS only, no redirects. The call has no overall time limit, but stalls of 20 s end it.
     */
    suspend fun openVideo(camera: String, size: StreamRequest? = null, audio: Boolean = false): VideoStream =
        openStream(listOf("video", camera, "2.0"), size, emptyList(), audio)

    /**
     * Opens recorded video of [camera] starting at [positionMs] (milliseconds since 1970), played by Blue Iris at
     * [speedPercent] (100 = normal) from its timeline (`/time/`), the way the web app does. Same container as live video.
     */
    suspend fun openRecorded(camera: String, positionMs: Long, speedPercent: Int, size: StreamRequest? = null, audio: Boolean = false): VideoStream =
        openStream(
            listOf("time", camera), size,
            listOf("opaque" to opaqueId, "pos" to positionMs.toString(), "speed" to speedPercent.coerceIn(10, 1600).toString()),
            audio,
        )

    /** A random label the web app also sends with recorded streams; it only has to stay the same for this run. */
    private val opaqueId = "sv" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)

    private suspend fun openStream(
        segments: List<String>,
        size: StreamRequest?,
        extra: List<Pair<String, String>>,
        audio: Boolean = false,
    ): VideoStream {
        if (session == null) throw SessionExpiredException()
        val url = baseUrl.newBuilder()
            .apply { segments.forEach { addPathSegment(it) } }
            .addQueryParameter("audio", if (audio) "1" else "0")
            .addQueryParameter("stream", "0")
            .apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }
            .apply {
                if (size != null) {
                    addQueryParameter("w", size.width.toString())
                    addQueryParameter("h", size.height.toString())
                    addQueryParameter("q", size.quality.toString())
                    addQueryParameter("kbps", size.kbps.toString())
                }
            }
            .addQueryParameter("extend", "2")
            .build()
        val streaming = http.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return withContext(Dispatchers.IO) {
            try {
                val response = streaming.newCall(Request.Builder().url(url).get().build()).execute()
                // Blue Iris answers with a picture or a short text instead of video when it has nothing for that time.
                val problem: BlueIrisException? = when {
                    response.isRedirect || response.code == 401 || response.code == 403 -> SessionExpiredException()
                    !response.isSuccessful -> BlueIrisException("Blue Iris answered HTTP ${response.code}.")
                    !response.header("Content-Type").orEmpty().startsWith("video/", ignoreCase = true) ->
                        BlueIrisException("Blue Iris did not send video.")
                    else -> null
                }
                if (problem != null) {
                    response.close()
                    throw problem
                }
                VideoStream(response)
            } catch (e: IOException) {
                throw BlueIrisException("Can't reach Blue Iris.")
            }
        }
    }

    /**
     * Diagnostic: opens the live stream of [camera] the way [variant] says, times how long Blue Iris takes to answer,
     * to send the stream header and to deliver the first complete video frame, then closes it. Used by the
     * "Test video start" tool in Settings to find out what slows a stream down. Returns one line of text.
     */
    suspend fun probeVideo(camera: String, size: StreamRequest, variant: ProbeVariant): String {
        val token = session ?: throw SessionExpiredException()
        val url = baseUrl.newBuilder()
            .addPathSegment("video")
            .addPathSegment(camera)
            .addPathSegment("2.0")
            .apply { if (variant == ProbeVariant.QueryToken) addQueryParameter("session", token) }
            .addQueryParameter("audio", "0")
            .addQueryParameter("stream", "0")
            .apply {
                if (variant != ProbeVariant.Bare) {
                    addQueryParameter("w", size.width.toString())
                    addQueryParameter("h", size.height.toString())
                    addQueryParameter("q", size.quality.toString())
                    addQueryParameter("kbps", size.kbps.toString())
                }
            }
            .addQueryParameter("extend", "2")
            .build()
        val builder = Request.Builder().url(url).get()
        when (variant) {
            ProbeVariant.Identity -> builder.header("Accept-Encoding", "identity")
            ProbeVariant.Browser -> {
                builder.header("Accept-Encoding", "identity")
                builder.header("Accept", "*/*")
                builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
                builder.header("Referer", baseUrl.toString())
            }
            else -> Unit
        }
        val client = http.newBuilder()
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
            .callTimeout(40, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .apply { if (variant == ProbeVariant.Http11) protocols(listOf(okhttp3.Protocol.HTTP_1_1)) }
            .build()
        return withContext(Dispatchers.IO) {
            val t0 = System.nanoTime()
            fun ms() = (System.nanoTime() - t0) / 1_000_000
            try {
                client.newCall(builder.build()).execute().use { response ->
                    val answered = ms()
                    if (!response.isSuccessful) return@use "${variant.label}: HTTP ${response.code} after $answered ms"
                    val reader = BlueStreamReader(response.body.source())
                    val head = reader.readHeader()
                    val headerAt = ms()
                    var frame: StreamBlock.Video? = null
                    while (frame == null) {
                        when (val block = reader.next()) {
                            is StreamBlock.Video -> frame = block
                            StreamBlock.End -> return@use "${variant.label}: stream ended at ${ms()} ms"
                            else -> Unit
                        }
                    }
                    "${variant.label} [${response.protocol}]: answered $answered ms, header $headerAt ms, " +
                        "first frame ${ms()} ms (${frame.data.size / 1024} KB, ${head.width}x${head.height})"
                }
            } catch (e: Exception) {
                "${variant.label}: failed after ${ms()} ms (${e.javaClass.simpleName})"
            }
        }
    }

    /** Best effort: ends the server session and forgets it here either way. */
    suspend fun logout() {
        val token = session ?: return
        try {
            call(buildJsonObject { put("cmd", "logout") }, checkResult = false)
        } catch (_: BlueIrisException) {
            // Signing out must always succeed locally.
        } finally {
            if (session == token) {
                session = null
                cookies.session = null
            }
        }
    }

    private fun reasonOf(envelope: Envelope): String? =
        (envelope.data as? JsonObject)?.get("reason")?.jsonPrimitive?.contentOrNull

    private suspend fun call(
        args: JsonObject,
        authed: Boolean = true,
        checkResult: Boolean = true,
    ): Envelope {
        val cmd = args["cmd"]?.jsonPrimitive?.content ?: "call"
        val body: JsonObject = if (authed) {
            val token = session ?: throw SessionExpiredException()
            JsonObject(args + ("session" to JsonPrimitive(token)))
        } else {
            args
        }
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("json").addQueryParameter("_", cmd).build())
            .post(body.toString().toRequestBody("text/plain".toMediaType()))
            .build()
        val text = withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    if (response.isRedirect) throw BlueIrisException("Blue Iris tried to redirect the request; redirects are not followed.")
                    if (!response.isSuccessful) throw BlueIrisException("Blue Iris answered HTTP ${response.code}.")
                    val source = response.body.source()
                    source.request(MAX_REPLY_BYTES + 1)
                    if (source.buffer.size > MAX_REPLY_BYTES) throw BlueIrisException("Blue Iris sent a reply that is too large.")
                    source.readUtf8()
                }
            } catch (e: IOException) {
                throw BlueIrisException("Can't reach Blue Iris.")
            }
        }
        val envelope = try {
            BlueIrisJson.decodeFromString(Envelope.serializer(), text)
        } catch (e: SerializationException) {
            throw BlueIrisException("Unexpected reply from Blue Iris.")
        } catch (e: IllegalArgumentException) {
            throw BlueIrisException("Unexpected reply from Blue Iris.")
        }
        if (checkResult && envelope.result != "success") {
            val reason = reasonOf(envelope).orEmpty()
            when {
                reason.contains("session", ignoreCase = true) || reason == "missing response" ->
                    throw SessionExpiredException()
                reason.contains("denied", ignoreCase = true) ->
                    throw AccessDeniedException(reason)
                else -> throw BlueIrisException(reason.ifEmpty { "Blue Iris reported an error." })
            }
        }
        return envelope
    }

    private companion object {
        const val MAX_REPLY_BYTES = 8L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 6L * 1024 * 1024
    }
}
