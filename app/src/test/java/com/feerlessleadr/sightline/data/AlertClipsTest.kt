package com.feerlessleadr.sightline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertClipsTest {
    @Test
    fun lengthIsReadFromBlueIrisSizeText() {
        assertEquals(20_000L, AlertClips.durationMillis("20 sec (391KB)"))
        assertEquals(65_000L, AlertClips.durationMillis("1 min 5 sec (2MB)"))
        assertEquals(7_200_000L, AlertClips.durationMillis("2 hr"))
        assertEquals(10_000L, AlertClips.durationMillis(""))
        assertEquals(10_000L, AlertClips.durationMillis(null))
        assertEquals(10_000L, AlertClips.durationMillis("(391KB)"))
    }

    @Test
    fun theAlertPlaysFromItsClipUnlessThatIsASnapshotPlaceholder() {
        assertEquals("@100.bvr", AlertClips.fileFor("@100.bvr", "@5.bvr"))
        assertEquals("@5.bvr", AlertClips.fileFor("@-1.bvr", "@5.bvr"))
        assertEquals("@5.bvr", AlertClips.fileFor("", "@5.bvr"))
    }

    @Test
    fun onlyBvrFilesCanBeReadAsFiles() {
        assertTrue(AlertClips.isPlayableFile("@100.bvr"))
        assertTrue(AlertClips.isPlayableFile("@100.BVR"))
        assertFalse(AlertClips.isPlayableFile("Cam1.20261125.mp4"))
    }
}
