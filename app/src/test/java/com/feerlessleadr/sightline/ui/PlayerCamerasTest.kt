package com.feerlessleadr.sightline.ui

import com.feerlessleadr.sightline.data.Camera
import com.feerlessleadr.sightline.data.CameraList
import com.feerlessleadr.sightline.data.LoginInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCamerasTest {
    private fun cam(id: String, hidden: Boolean = false) = Camera(optionDisplay = id, optionValue = id, hidden = hidden)
    private val all = Camera(optionDisplay = "All cameras", optionValue = "index", group = listOf("a", "b", "c", "d"))
    private val porch = Camera(optionDisplay = "+Porch", optionValue = "Porch", group = listOf("c", "a"))
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
}
