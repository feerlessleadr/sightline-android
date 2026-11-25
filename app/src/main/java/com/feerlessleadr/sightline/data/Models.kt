package com.feerlessleadr.sightline.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

internal val BlueIrisJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** The envelope around every Blue Iris JSON reply. */
@Serializable
internal data class Envelope(
    val result: String? = null,
    val session: String? = null,
    val data: JsonElement? = null,
)

/** What the app keeps from a successful sign-in. Deliberately omits the server's location fields. */
@Serializable
data class LoginInfo(
    val systemName: String?,
    val version: String?,
    val user: String?,
    val admin: Boolean,
    val canPtz: Boolean,
    val canExportClips: Boolean,
)

/** One `camlist` entry: a camera, or a group (which has [group]). */
@Serializable
data class Camera(
    val optionDisplay: String? = null,
    val optionValue: String = "",
    val isEnabled: Boolean = true,
    val hidden: Boolean = false,
    val isOnline: Boolean? = null,
    val isNoSignal: Boolean = false,
    val isMotion: Boolean = false,
    val isTriggered: Boolean = false,
    val isAlerting: Boolean = false,
    val isRecording: Boolean = false,
    val ptz: Boolean = false,
    val webcast: Boolean? = null,
    val width: Int? = null,
    val height: Int? = null,
    val xsize: Int? = null,
    val ysize: Int? = null,
    val group: List<String>? = null,
    val rects: List<List<Int>>? = null,
) {
    val isGroup: Boolean get() = group != null

    /** Blue Iris prefixes group names with "+"; the name shown to people leaves it off. */
    val displayName: String
        get() = (optionDisplay?.trim()?.trimStart('+')?.trim()).orEmpty().ifEmpty { optionValue }

    val online: Boolean get() = isOnline != false && !isNoSignal
}

/** The parsed `camlist`: groups and cameras, with Blue Iris's special "@index" cycle entry removed. */
data class CameraList(val groups: List<Camera>, val cameras: List<Camera>) {

    /** Cameras to show for [group] (all cameras when null), in the group's own order. */
    fun visibleCameras(group: Camera?, showHidden: Boolean = false): List<Camera> {
        val usable = cameras.filter { it.isEnabled && (showHidden || !it.hidden) }
        val names = group?.group ?: return usable
        val order = names.withIndex().associate { it.value to it.index }
        return usable.filter { it.optionValue in order }.sortedBy { order.getValue(it.optionValue) }
    }

    companion object {
        internal fun parse(data: JsonElement?): CameraList {
            val array = data as? JsonArray ?: throw BlueIrisException("Unexpected camera list from Blue Iris.")
            val entries = array.mapNotNull { element ->
                runCatching { BlueIrisJson.decodeFromJsonElement(Camera.serializer(), element) }.getOrNull()
            }.filter { it.optionValue.isNotBlank() && !it.optionValue.startsWith("@") }
            val (groups, cameras) = entries.partition { it.isGroup }
            return CameraList(groups = groups.filter { !it.hidden }, cameras = cameras)
        }
    }
}
