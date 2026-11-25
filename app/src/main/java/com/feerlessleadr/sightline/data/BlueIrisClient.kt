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

/** Something went wrong talking to Blue Iris. The message is safe to show to the user. */
open class BlueIrisException(message: String) : Exception(message)

/** Wrong user name or password, or Blue Iris refused the sign-in. */
class LoginFailedException(message: String) : BlueIrisException(message)

/** The session ended; sign in again. */
class SessionExpiredException : BlueIrisException("Your session expired. Please sign in again.")

/** The signed-in account is not allowed to do this (for example the system log for a non-admin). */
class AccessDeniedException(message: String) : BlueIrisException(message)

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
