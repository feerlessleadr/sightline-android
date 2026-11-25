package com.feerlessleadr.sightline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    private fun ok(input: String): String =
        (ServerAddress.parse(input) as AddressResult.Ok).url.toString()

    private fun bad(input: String) = ServerAddress.parse(input) as AddressResult.Bad

    @Test fun `bare host gets https and a trailing slash`() {
        assertEquals("https://cams.example.test/", ok("cams.example.test"))
    }

    @Test fun `https address is kept and port is allowed`() {
        assertEquals("https://cams.example.test:8443/", ok(" https://cams.example.test:8443 "))
    }

    @Test fun `reverse proxy sub path is kept with trailing slash`() {
        assertEquals("https://example.test/blueiris/", ok("https://example.test/blueiris"))
    }

    @Test fun `http is refused in any case`() {
        assertTrue(bad("http://cams.example.test").reason.contains("https"))
        assertTrue(bad("HTTP://cams.example.test").reason.contains("https"))
    }

    @Test fun `empty and nonsense are refused`() {
        bad("")
        bad("   ")
        bad("https://")
        bad("ftp://cams.example.test")
    }

    @Test fun `user info query and fragment are refused`() {
        bad("https://user:pw@cams.example.test")
        bad("https://cams.example.test/?session=abc")
        bad("https://cams.example.test/#x")
    }
}
