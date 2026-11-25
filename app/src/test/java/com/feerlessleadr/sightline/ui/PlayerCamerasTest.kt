package com.feerlessleadr.sightline.ui

import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.CameraList
import com.feerlessleadr.sightline.data.LoginInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCamerasTest {
    private fun cam(id: String, hidden: Boolean = false) = Camera(optionDisplay = id, optionValue = id, hidden = hidden)
    private val all = Camera(optionDisplay = "All cameras", optionValue = "index", group = listOf("a", "b", "c", "d"), rects = listOf(listOf(0, 0, 1, 1)), width = 100, height = 100)
    private val porch = Camera(optionDisplay = "+Porch", optionValue = "Porch", group = listOf("c", "a"), rects = listOf(listOf(0, 0, 1, 1)), width = 100, height = 100)
    private val list = CameraList(listOf(all, porch), listOf(cam("a"), cam("b"), cam("c"), cam("d", hidden = true)))
    private val info = LoginInfo(systemName = "x", version = null, user = null, admin = false, canPtz = false, canExportClips = false)

    private fun session(group: String? = null, arrangement: GroupArrangement? = null) = SessionState(
        info = info,
        cameras = list,
        selectedGroup = group,
        arrangements = if (arrangement != null) mapOf((group ?: "index") to arrangement) else emptyMap(),
    )

    @Test
    fun swipingCyclesThroughTheShownCamerasOfTheGridsGroup() {
        assertEquals(listOf("a", "b", "c"), session().playerCameras("a").map { it.optionValue })
        assertEquals(listOf("c", "a"), session("Porch").playerCameras("c").map { it.optionValue })
    }

    @Test
    fun theOwnersOrderIsFollowed() {
        val s = session(arrangement = GroupArrangement(order = listOf("c", "b", "a")))
        assertEquals(listOf("c", "b", "a"), s.playerCameras("b").map { it.optionValue })
    }

    @Test
    fun aCameraOutsideTheGroupFallsBackToAllShownCameras() {
        assertEquals(listOf("a", "b", "c"), session("Porch").playerCameras("b").map { it.optionValue })
    }

    @Test
    fun theGroupButtonOpensTheGridsGroupOrAllCameras() {
        // "Porch" holds c and a; the grid shows it: watching c opens Porch. Watching b (not in Porch) opens All cameras.
        assertEquals("Porch", session("Porch").groupFor("c")?.optionValue)
        assertEquals("index", session("Porch").groupFor("b")?.optionValue)
        assertEquals("index", session().groupFor("a")?.optionValue)
    }

    @Test
    fun fromAGroupTheButtonGoesBackToWhereItWasOpenedFrom() {
        val s = session()
        assertEquals("b", s.cameraToReturnTo(all, "b")?.optionValue)
        assertEquals("a", s.cameraToReturnTo(all, null)?.optionValue) // else the group's first shown camera
        assertEquals("a", s.cameraToReturnTo(all, "d")?.optionValue) // hidden: not offered
    }

    @Test
    fun swipingInAGroupMovesBetweenGroupsWithALayout() {
        val withLayout = all.copy(rects = listOf(listOf(0, 0, 1, 1)), width = 100, height = 100)
        val porchLayout = porch.copy(rects = listOf(listOf(0, 0, 1, 1)), width = 100, height = 100)
        val s = SessionState(info = info, cameras = CameraList(listOf(withLayout, porchLayout, porch.copy(optionValue = "Bare", rects = null)), list.cameras))
        assertEquals(listOf("index", "Porch"), s.playerCameras("index").map { it.optionValue })
    }
}
