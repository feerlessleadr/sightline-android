package com.feerlessleadr.sightline.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** All data here is invented. Real server samples contain names and locations and never enter the repo. */
class BlueIrisClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BlueIrisClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = BlueIrisClient(server.url("/"))
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun reply(body: String) = server.enqueue(MockResponse().setBody(body))

    private val challenge = """{"result":"fail","session":"sess1","data":{"reason":"missing response"}}"""
    private val loginOk = """
        {"result":"success","session":"sess1","data":{"system name":"Test Server","version":"6.0.0.0","user":"viewer",
        "admin":false,"ptz":true,"clipcreate":true,"latitude":1.5,"longitude":2.5}}
    """.trimIndent()

    @Test fun `login answers the challenge with md5 of user session password`() = runBlocking {
        reply(challenge)
        reply(loginOk)
        val info = client.login("viewer", "pw")
        assertEquals("Test Server", info.systemName)
        assertEquals("6.0.0.0", info.version)
        assertFalse(info.admin)
        assertTrue(info.canPtz)

        server.takeRequest() // the challenge request
        val second = server.takeRequest()
        val text = second.body.readUtf8()
        val body = Json.parseToJsonElement(text).jsonObject
        assertEquals("login", body["cmd"]!!.jsonPrimitive.content)
        assertEquals("sess1", body["session"]!!.jsonPrimitive.content)
        assertEquals(md5Hex("viewer:sess1:pw"), body["response"]!!.jsonPrimitive.content)
        assertFalse("password must never be sent", text.contains("\"pw\""))
        assertEquals("POST", second.method)
        assertEquals("/json?_=login", second.path)
    }

    @Test fun `login that needs no authentication skips the second call`() = runBlocking {
        reply(loginOk)
        client.login("viewer", "pw")
        assertEquals(1, server.requestCount)
    }

    @Test fun `wrong password gives a login failure with the server's reason`() {
        reply(challenge)
        reply("""{"result":"fail","data":{"reason":"Invalid credentials"}}""")
        try {
            runBlocking { client.login("viewer", "bad") }
            fail("expected LoginFailedException")
        } catch (e: LoginFailedException) {
            assertEquals("Invalid credentials", e.message)
        }
    }

    private val camlist = """
        {"result":"success","session":"sess1","data":[
          {"optionDisplay":"+All cameras","optionValue":"index","group":["Cam2","Cam1"],"rects":[[0,0,10,10],[10,0,20,10]],"xsize":2,"ysize":1,"width":20,"height":10,"hidden":false},
          {"optionDisplay":"+All cameras cycle","optionValue":"@index"},
          {"optionDisplay":"+Outside","optionValue":"Outside","group":["Cam2"]},
          {"optionDisplay":"Front","optionValue":"Cam1","isEnabled":true,"isOnline":true,"isNoSignal":false,"ptz":true,"hidden":false},
          {"optionDisplay":"Back","optionValue":"Cam2","isEnabled":true,"isOnline":true,"isTriggered":true},
          {"optionDisplay":"Secret","optionValue":"Cam3","isEnabled":true,"hidden":true},
          {"optionDisplay":"Off","optionValue":"Cam4","isEnabled":false}
        ]}
    """.trimIndent()

    private fun signedInClient(): BlueIrisClient {
        reply(loginOk)
        runBlocking { client.login("viewer", "pw") }
        server.takeRequest()
        return client
    }

    @Test fun `camlist is parsed, cycle entry dropped, hidden and disabled filtered, group order kept`() = runBlocking {
        signedInClient()
        reply(camlist)
        val list = client.cameras()
        assertEquals(listOf("index", "Outside"), list.groups.map { it.optionValue })
        assertEquals("All cameras", list.groups.first().displayName)
        val all = list.groups.first()
        assertEquals(listOf("Cam2", "Cam1"), list.visibleCameras(all).map { it.optionValue })
        assertEquals(listOf("Cam1", "Cam2"), list.visibleCameras(null).map { it.optionValue })
        assertEquals(listOf("Cam1", "Cam2", "Cam3"), list.visibleCameras(null, showHidden = true).map { it.optionValue })
        assertEquals(listOf("Cam2"), list.visibleCameras(list.groups[1]).map { it.optionValue })
    }

    @Test fun `session is sent in the body and as a cookie, never in the url`() = runBlocking {
        signedInClient()
        reply(camlist)
        client.cameras()
        val request = server.takeRequest()
        assertEquals("/json?_=camlist", request.path)
        assertFalse(request.path!!.contains("sess1"))
        assertEquals("session=sess1", request.getHeader("Cookie"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("sess1", body["session"]!!.jsonPrimitive.content)
        assertEquals("camlist", body["cmd"]!!.jsonPrimitive.content)
    }

    @Test fun `calls before sign in say the session expired and send nothing`() {
        try {
            runBlocking { client.cameras() }
            fail("expected SessionExpiredException")
        } catch (e: SessionExpiredException) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `a session failure reason maps to SessionExpired`() {
        signedInClient()
        reply("""{"result":"fail","data":{"reason":"Invalid session"}}""")
        try {
            runBlocking { client.cameras() }
            fail("expected SessionExpiredException")
        } catch (_: SessionExpiredException) {
        }
    }

    @Test fun `access denied maps to AccessDenied`() {
        signedInClient()
        reply("""{"result":"fail","data":{"reason":"Access denied"}}""")
        try {
            runBlocking { client.cameras() }
            fail("expected AccessDeniedException")
        } catch (e: AccessDeniedException) {
            assertEquals("Access denied", e.message)
        }
    }

    @Test fun `redirects are not followed`() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/elsewhere").toString()))
        try {
            runBlocking { client.login("viewer", "pw") }
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertTrue(e.message!!.contains("redirect"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `garbage and oversized replies are refused`() {
        reply("not json")
        try {
            runBlocking { client.login("viewer", "pw") }
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertEquals("Unexpected reply from Blue Iris.", e.message)
        }
        server.enqueue(MockResponse().setBody("x".repeat(9 * 1024 * 1024)))
        try {
            runBlocking { client.login("viewer", "pw") }
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test fun `server error status and unreachable server give friendly errors`() {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            runBlocking { client.login("viewer", "pw") }
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertEquals("Blue Iris answered HTTP 500.", e.message)
        }
        server.shutdown()
        try {
            runBlocking { client.login("viewer", "pw") }
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertEquals("Can't reach Blue Iris.", e.message)
        }
    }

    @Test fun `still uses the cookie only and keeps the token out of the url`() = runBlocking {
        signedInClient()
        server.enqueue(MockResponse().addHeader("Content-Type", "image/jpeg").setBody("jpegbytes"))
        val bytes = client.still("Cam 1", width = 99999, quality = 1)
        assertEquals("jpegbytes", String(bytes))
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/image/Cam%201?w=1920&q=10", request.path)
        assertFalse(request.path!!.contains("sess1"))
        assertEquals("session=sess1", request.getHeader("Cookie"))
    }

    @Test fun `still refuses non-images, redirects, oversize and signed-out use`() {
        try {
            runBlocking { client.still("Cam1") }
            fail("expected SessionExpiredException")
        } catch (_: SessionExpiredException) {
            assertEquals(0, server.requestCount)
        }
        signedInClient()
        server.enqueue(MockResponse().addHeader("Content-Type", "text/html").setBody("<html>"))
        try {
            runBlocking { client.still("Cam1") }
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertEquals("Blue Iris did not send a picture.", e.message)
        }
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/login"))
        try {
            runBlocking { client.still("Cam1") }
            fail("expected SessionExpiredException")
        } catch (_: SessionExpiredException) {
        }
        server.enqueue(MockResponse().addHeader("Content-Type", "image/jpeg").setBody("x".repeat(7 * 1024 * 1024)))
        try {
            runBlocking { client.still("Cam1") }
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test fun `video opens with the cookie only and refuses non-video replies`() = runBlocking {
        signedInClient()
        server.enqueue(MockResponse().addHeader("Content-Type", "video/mpeg").setBody("blue"))
        client.openVideo("Cam 1").use { stream -> assertEquals("blue", stream.source.readUtf8()) }
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/video/Cam%201/2.0?audio=0&stream=0&extend=2", request.path)
        assertEquals("session=sess1", request.getHeader("Cookie"))

        server.enqueue(MockResponse().addHeader("Content-Type", "text/html").setBody("<html>"))
        try {
            client.openVideo("Cam1")
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertEquals("Blue Iris did not send video.", e.message)
        }
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            client.openVideo("Cam1")
            fail("expected SessionExpiredException")
        } catch (_: SessionExpiredException) {
        }
    }

    @Test fun `a restored session is sent as the cookie and body, and exposes its token`() = runBlocking {
        client.restore("saved9")
        assertEquals("saved9", client.sessionToken)
        reply(camlist)
        client.cameras()
        val request = server.takeRequest()
        assertEquals("session=saved9", request.getHeader("Cookie"))
        assertEquals("saved9", Json.parseToJsonElement(request.body.readUtf8()).jsonObject["session"]!!.jsonPrimitive.content)
    }

    @Test fun `logout clears the session even when the server fails`() = runBlocking {
        signedInClient()
        server.enqueue(MockResponse().setResponseCode(500))
        client.logout()
        assertNotNull(server.takeRequest())
        try {
            client.cameras()
            fail("expected SessionExpiredException")
        } catch (_: SessionExpiredException) {
        }
    }

    @Test fun `recorded video asks for a time and speed with the cookie only`() = runBlocking {
        signedInClient()
        server.enqueue(MockResponse().addHeader("Content-Type", "video/mpeg").setBody("blue"))
        client.openRecorded("Cam 1", 1_800_000_000_123L, 400).use { }
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/time/Cam%201?audio=0&stream=0&opaque=sv"))
        assertTrue(request.path!!.contains("&pos=1800000000123&speed=400&extend=2"))
        assertEquals("session=sess1", request.getHeader("Cookie"))
    }

    @Test fun `timeline and camera alerts are requested and read`() = runBlocking {
        signedInClient()
        reply("""{"result":"success","data":{"clips":[{"x1":0,"x2":4}],"alerts":[{"x1":1,"x2":2,"type":134217728}]}}""")
        val t = client.timeline("Cam1", 1_800_000_000_500L, 1_800_000_060_000L, 256)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("timeline", body["cmd"]!!.jsonPrimitive.content)
        assertEquals("1800000000", body["startdate"]!!.jsonPrimitive.content)
        assertEquals("256", body["msecpp"]!!.jsonPrimitive.content)
        assertEquals(1_800_000_000_000L, t.clips[0].startMs)
        assertEquals(1024L, t.clips[0].lengthMs)
        assertEquals(1L shl 27, t.alerts[0].flags)

        reply("""{"result":"success","data":[{"camera":"Cam1","date":1800000100,"memo":"person:90%","flags":67108864,"path":"@1.bvr","clip":"@2.bvr","offset":500},{"camera":"Cam1","date":1800000000,"memo":"","flags":0,"path":"@0.bvr","clip":"","offset":0}]}""")
        val alerts = client.cameraAlerts("Cam1", 0L, 2_000_000_000_000L)
        assertEquals(listOf(1_800_000_000_000L, 1_800_000_100_000L), alerts.map { it.timeMs })
        assertEquals("person:90%", alerts[1].memo)
    }

    @Test fun `an alert file is opened by name with an offset and speed, cookie only`() = runBlocking {
        signedInClient()
        server.enqueue(MockResponse().addHeader("Content-Type", "video/mpeg").setBody("blue"))
        client.openClip("@100.bvr", 2500L, 200).use { }
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/file/clips/@100.bvr?audio=0&stream=0&speed=200&time=2500&extend=2"))
        assertEquals("session=sess1", request.getHeader("Cookie"))
    }

    @Test fun `alert details include the size text`() = runBlocking {
        signedInClient()
        reply("""{"result":"success","data":[{"camera":"Cam1","date":1800000100,"memo":"","flags":0,"path":"@1.bvr","clip":"@2.bvr","offset":500,"filesize":"20 sec (391KB)"}]}""")
        assertEquals("20 sec (391KB)", client.cameraAlerts("Cam1", 0L, 2_000_000_000_000L)[0].fileSize)
    }

    @Test fun `an export is asked for with the whole request and the answer says where it will be`() = runBlocking {
        signedInClient()
        reply("""{"result":"success","data":{"path":"@9.mp4","uri":"Export/9.mp4"}}""")
        val start = client.startExport(ExportRequest("@100.bvr", 2000L, 25000L, ExportOptions(reencode = false, profile = 2, audio = false, overlay = true)))
        assertEquals("@9.mp4", start.path)
        assertEquals("Export/9.mp4", start.uri)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("export", body["cmd"]!!.jsonPrimitive.content)
        assertEquals("@100.bvr", body["path"]!!.jsonPrimitive.content)
        assertEquals("2", body["profile"]!!.jsonPrimitive.content)
        assertEquals("false", body["reencode"]!!.jsonPrimitive.content)
        assertEquals("false", body["overlay"]!!.jsonPrimitive.content) // no overlay without re-encoding
        assertEquals("false", body["audio"]!!.jsonPrimitive.content)
        assertEquals("2000", body["startms"]!!.jsonPrimitive.content)
        assertEquals("25000", body["msec"]!!.jsonPrimitive.content)
    }

    @Test fun `recording files are listed from a day and a half earlier`() = runBlocking {
        signedInClient()
        reply("""{"result":"success","data":[{"path":"@5.bvr","date":1000000,"msec":60000}]}""")
        val files = client.recordingFiles("Cam1", 2_000_000_000L, 2_000_100_000L)
        assertEquals(listOf("@5.bvr"), files.map { it.path })
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("cliplist", body["cmd"]!!.jsonPrimitive.content)
        assertEquals("Cam1", body["camera"]!!.jsonPrimitive.content)
        assertEquals((2_000_000L - 36 * 3600).toString(), body["startdate"]!!.jsonPrimitive.content)
        assertEquals("all", body["view"]!!.jsonPrimitive.content)
    }

    @Test fun `a Blue Iris that does not know the view option is asked again without it`() = runBlocking {
        signedInClient()
        reply("""{"result":"fail","data":{"reason":"unknown option"}}""")
        reply("""{"result":"success","data":[{"path":"@5.bvr","date":1000000,"msec":60000}]}""")
        assertEquals(1, client.recordingFiles("Cam1", 2_000_000_000L, 2_000_100_000L).size)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"view\""))
        assertFalse(server.takeRequest().body.readUtf8().contains("\"view\""))
    }

    @Test fun `an alert thumbnail is fetched by record id with the cookie only`() = runBlocking {
        signedInClient()
        server.enqueue(MockResponse().addHeader("Content-Type", "image/jpeg").setBody("thumbbytes"))
        assertEquals("thumbbytes", String(client.thumbnail("123")))
        val request = server.takeRequest()
        assertEquals(listOf("thumbs", "@123"), request.requestUrl!!.pathSegments)
        assertEquals(null, request.requestUrl!!.queryParameter("session"))
        assertTrue(request.getHeader("Cookie").orEmpty().contains("session"))
        try {
            client.thumbnail("")
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertEquals("This alert has no picture.", e.message)
        }
    }

    @Test fun `a refused export explains itself`() = runBlocking {
        signedInClient()
        reply("""{"result":"fail","data":{"reason":"Access denied"}}""")
        try {
            client.startExport(ExportRequest("@1.bvr", 0, 1000, ExportOptions()))
            fail("expected BlueIrisException")
        } catch (e: BlueIrisException) {
            assertEquals("Your Blue Iris user isn't allowed to export clips.", e.message)
        }
    }

    @Test fun `the export queue and a finished clip are read`() = runBlocking {
        signedInClient()
        reply("""{"result":"success","data":[{"path":"@9.mp4","uri":"Export/9.mp4","status":"active","progress":42.5},{"path":"@8.mp4","status":"done","uri":"x"}]}""")
        val queue = client.exportQueue()
        assertEquals(listOf("active", "done"), queue.map { it.status })
        assertEquals(42.5, queue[0].progress, 0.001)

        server.enqueue(MockResponse().addHeader("Content-Type", "video/mp4").setBody("clipbytes"))
        val out = java.io.ByteArrayOutputStream()
        var seen = 0L
        client.downloadExport("Export/9.mp4", out) { done, _ -> seen = done }
        assertEquals("clipbytes", out.toString())
        assertEquals(9L, seen)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/clips/Export/9.mp4", request.path)
        assertEquals("session=sess1", request.getHeader("Cookie"))
    }

    @Test fun `a Windows style export name is fetched with slashes`() = runBlocking {
        signedInClient()
        server.enqueue(MockResponse().addHeader("Content-Type", "video/mp4").setBody("x"))
        client.downloadExport("Clipboard\\FrontDrive.20261125_193758715Z-193859Z.5.mp4", java.io.ByteArrayOutputStream()) { _, _ -> }
        assertEquals("/clips/Clipboard/FrontDrive.20261125_193758715Z-193859Z.5.mp4", server.takeRequest().path)
    }

    @Test fun `a server supplied path that climbs out of its folder is refused before any request`() = runBlocking {
        signedInClient()
        val before = server.requestCount
        for (bad in listOf("../json", "Export/../../admin", "Export\\..\\x.mp4", "./x.mp4", "..")) {
            try {
                client.downloadExport(bad, java.io.ByteArrayOutputStream()) { _, _ -> }
                fail("accepted $bad")
            } catch (e: BlueIrisException) {
                // expected
            }
        }
        for (bad in listOf("..", ".")) {
            try { client.openClip(bad, 0L, 100); fail("accepted clip $bad") } catch (e: BlueIrisException) { }
            try { client.still(bad); fail("accepted camera $bad") } catch (e: BlueIrisException) { }
            try { client.recordedStill(bad, 0L); fail("accepted camera $bad") } catch (e: BlueIrisException) { }
        }
        assertEquals(before, server.requestCount)
    }

    @Test fun `names with dots inside are still allowed`() {
        requireSafePath("Export/9.mp4")
        requireSafePath("Clipboard\\Front.Drive..5.mp4")
        requireSafePath("@100.bvr")
    }
}
