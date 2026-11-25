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
}
