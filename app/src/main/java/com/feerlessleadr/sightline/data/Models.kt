package com.feerlessleadr.sightline.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

internal val BlueIrisJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** A number Blue Iris sometimes sends as text (`"4096"`); anything else that is not a number reads as missing. */
internal object LenientDouble : kotlinx.serialization.KSerializer<Double?> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("LenientDouble", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Double? {
        val element = (decoder as? kotlinx.serialization.json.JsonDecoder)?.decodeJsonElement() as? kotlinx.serialization.json.JsonPrimitive
        return element?.content?.toDoubleOrNull()
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
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
    /** Frames per second Blue Iris reports for the camera (`FPS`); used for frame step. */
    @SerialName("FPS") val fps: Double? = null,
    /** Bytes a second Blue Iris records for the camera (`BPS`, sometimes sent as text); used to estimate download sizes. */
    @SerialName("BPS") @Serializable(with = LenientDouble::class) val bps: Double? = null,
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

/** A stretch of time, [startMs] milliseconds since 1970 and [lengthMs] long. */
data class TimeSpan(val startMs: Long, val lengthMs: Long)

/** An alert mark on the timeline; [flags] are Blue Iris's alert flag bits (person, vehicle, wildlife, ...). */
data class AlertMark(val startMs: Long, val lengthMs: Long, val flags: Long)

/** Where a camera has recordings and alerts in a period (the `timeline` command). */
data class Timeline(val clips: List<TimeSpan>, val alerts: List<AlertMark>) {
    companion object {
        val Empty = Timeline(emptyList(), emptyList())

        /** The reply gives each span as `x1`/`x2` step numbers counted from [originMs] at [msecPerStep] each. */
        internal fun parse(data: JsonElement?, originMs: Long, msecPerStep: Long): Timeline {
            val obj = data as? kotlinx.serialization.json.JsonObject ?: return Empty
            fun number(o: kotlinx.serialization.json.JsonObject, key: String) =
                (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
            fun spans(key: String) = (obj[key] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val x1 = number(o, "x1") ?: return@mapNotNull null
                val x2 = number(o, "x2") ?: return@mapNotNull null
                Triple(originMs + (x1 * msecPerStep).toLong(), maxOf(1.0, x2 - x1) * msecPerStep, number(o, "type")?.toLong() ?: 0L)
            }
            return Timeline(
                clips = spans("clips").map { TimeSpan(it.first, it.second.toLong()) },
                alerts = spans("alerts").map { AlertMark(it.first, it.second.toLong(), it.third) },
            )
        }
    }
}

/** One alert of a camera: when it happened, its AI text and where its recording is. */
data class AlertItem(
    val camera: String,
    val memo: String,
    val timeMs: Long,
    val flags: Long,
    val path: String,
    val clip: String,
    val offsetMs: Long,
    /** Blue Iris's size text for the alert's recording, like `20 sec (391KB)`; its length tells how long the alert is. */
    val fileSize: String = "",
) {
    companion object {
        internal fun parse(element: JsonElement): AlertItem? {
            val o = element as? kotlinx.serialization.json.JsonObject ?: return null
            fun text(key: String) = (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            fun num(key: String) = text(key).toDoubleOrNull()?.toLong() ?: 0L
            val camera = text("camera").ifEmpty { return null }
            val date = num("date")
            if (date <= 0L) return null
            return AlertItem(camera, text("memo"), date * 1000, num("flags"), text("path"), text("clip"), num("offset"), text("filesize"))
        }
    }
}
