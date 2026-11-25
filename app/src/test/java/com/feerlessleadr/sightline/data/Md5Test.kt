package com.feerlessleadr.sightline.data

import org.junit.Assert.assertEquals
import org.junit.Test

class Md5Test {
    @Test fun `known vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", md5Hex("The quick brown fox jumps over the lazy dog"))
    }
}
