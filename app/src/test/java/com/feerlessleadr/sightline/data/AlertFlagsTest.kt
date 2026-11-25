package com.feerlessleadr.sightline.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFlagsTest {
    @Test
    fun onlyTheChosenBitChanges() {
        val wildlife = 1L shl 31 // beyond a 32-bit int
        val flags = AlertFlags.with(wildlife, AlertFlags.FLAGGED, true)
        assertEquals(wildlife + 2, flags)
        assertTrue(AlertFlags.has(flags, AlertFlags.FLAGGED))
        assertFalse(AlertFlags.has(flags, AlertFlags.PROTECTED))
        assertEquals(wildlife, AlertFlags.with(flags, AlertFlags.FLAGGED, false))
        assertEquals(flags, AlertFlags.with(flags, AlertFlags.FLAGGED, true)) // already set: unchanged
        assertEquals(wildlife + 4, AlertFlags.with(wildlife, AlertFlags.PROTECTED, true))
    }

    @Test
    fun recordIdIsThePlainNumberOfThePath() {
        assertEquals("123", AlertFlags.recordId("@123.bvr"))
        assertEquals("45", AlertFlags.recordId("@45"))
        assertEquals("", AlertFlags.recordId(null))
        assertEquals("", AlertFlags.recordId("@.bvr"))
    }

    @Test
    fun permissionProblemsGetAPlainMessage() {
        assertEquals("This Blue Iris user isn't allowed to change alerts.", AlertFlags.friendlyError("Access denied"))
        assertEquals("Something odd", AlertFlags.friendlyError("Something odd"))
        assertEquals("Blue Iris didn't accept the change.", AlertFlags.friendlyError(null))
    }

    @Test
    fun updateSendsTheWholeFlagsValueForTheRecord() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            val client = BlueIrisClient(server.url("/"))
            client.restore("sess1")
            server.enqueue(MockResponse().setBody("""{"result":"success","data":{}}"""))
            client.setAlertFlags("@123.bvr", (1L shl 31) + 2)
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("update", body["cmd"]!!.jsonPrimitive.content)
            assertEquals("@123", body["path"]!!.jsonPrimitive.content)
            assertEquals(((1L shl 31) + 2).toString(), body["flags"]!!.jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }
}
