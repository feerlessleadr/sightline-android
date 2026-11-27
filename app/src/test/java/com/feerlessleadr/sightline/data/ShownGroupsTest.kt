package com.feerlessleadr.sightline.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShownGroupsTest {
    private val groups = listOf(
        Camera(optionValue = "index", optionDisplay = "All cameras", group = listOf("A", "B")),
        Camera(optionValue = "Front", optionDisplay = "Front", group = listOf("A")),
        Camera(optionValue = "Back", optionDisplay = "Back", group = listOf("B")),
    )

    @Test
    fun hiddenGroupsAreLeftOut() {
        assertEquals(listOf("index", "Back"), groups.shownGroups(setOf("Front")).map { it.optionValue })
        assertEquals(groups.map { it.optionValue }, groups.shownGroups(emptySet()).map { it.optionValue })
    }

    @Test
    fun neverNoneAtAll() {
        // Everything hidden (for example every group was removed in Blue Iris but the old choice stayed): show them all.
        assertEquals(3, groups.shownGroups(setOf("index", "Front", "Back")).size)
    }
}
