package com.example.skywallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.math.min
import kotlin.math.sqrt

internal object GradientCodec {
    fun encode(value: SkyGradient): String = JSONObject().apply {
        put("stops", JSONArray().apply { value.stops.forEach { stop ->
            put(JSONObject().put("color", stop.color).put("position", stop.position.toDouble()))
        } })
        fun point(name: String, point: GradientPoint) = put(name, JSONArray(listOf(point.x.toDouble(), point.y.toDouble())))
        point("start", value.start); point("end", value.end); point("center", value.center)
        put("radius", value.radius.toDouble())
    }.toString()

    fun decode(source: String): SkyGradient = JSONObject(source).let { json ->
        val stops = json.getJSONArray("stops")
        fun point(name: String) = json.getJSONArray(name).let { GradientPoint(it.getDouble(0).toFloat(), it.getDouble(1).toFloat()) }
        SkyGradient((0 until stops.length()).map { i -> stops.getJSONObject(i).let {
            GradientStop(it.getInt("color") or (0xFF shl 24), it.getDouble("position").toFloat())
        } }, point("start"), point("end"), point("center"), json.getDouble("radius").toFloat())
    }
}

/** Images and gradient settings share the Direct Boot store with the solar settings. */
class WallpaperStore(private val context: Context, private val prefs: SharedPreferences) {
    internal companion object {
        private val configurationLock = Any()
        fun gradientKey(phase: SkyPhase, type: GradientType) = "gradient_${type.name}_${phase.name}"
    }
    private val directory get() = File(context.createDeviceProtectedStorageContext().filesDir, "wallpapers")
    private var anchorKey: Pair<Instant, GeoPoint>? = null
    private var anchors = emptyList<GradientAnchor>()
    val mode: WallpaperMode get() = WallpaperMode.entries.find { it.name == prefs.getString("wallpaper_mode", null) } ?: WallpaperMode.IMAGE
    val type: GradientType get() = GradientType.entries.find { it.name == prefs.getString("gradient_type", null) } ?: GradientType.LINEAR
    init {
        withConfiguration {
            if (!prefs.getBoolean("gradient_types_migrated", false)) {
                val owner = type
                val edit = prefs.edit()
                SkyPhase.entries.forEach { phase ->
                    val legacy = prefs.getString("gradient_${phase.name}", null)
                    if (legacy != null && !prefs.contains(gradientKey(phase, owner)) && runCatching { GradientCodec.decode(legacy) }.isSuccess)
                        edit.putString(gradientKey(phase, owner), legacy)
                }
                // Old records contain no type information. Keep their exact bytes as a recovery copy,
                // and assign the surviving configuration only to the type selected before migration.
                edit.putBoolean("gradient_types_migrated", true).commit()
            }
        }
    }
    internal fun <T> withConfiguration(block: () -> T): T = synchronized(configurationLock, block)
    fun setMode(mode: WallpaperMode) { withConfiguration { prefs.edit().putString("wallpaper_mode", mode.name).apply() } }
    fun setType(type: GradientType) { withConfiguration { prefs.edit().putString("gradient_type", type.name).apply() } }
    fun gradient(phase: SkyPhase, type: GradientType = this.type): SkyGradient = prefs.getString(gradientKey(phase, type), null)?.let {
        runCatching { GradientCodec.decode(it) }.getOrNull()
    } ?: SkyGradient.default(phase, type)
    fun saveGradient(phase: SkyPhase, value: SkyGradient, type: GradientType = this.type) {
        withConfiguration { prefs.edit().putString(gradientKey(phase, type), GradientCodec.encode(value)).apply() }
    }

    fun frame(now: Instant, point: GeoPoint?, automatic: Boolean, phase: SkyPhase, type: GradientType = this.type): GradientFrame {
        if (!automatic || point == null) return GradientFrame.from(gradient(phase, type))
        val key = SolarClock.dayStart(now, point) to point
        if (anchorKey != key) {
            anchors = SolarGradientClock.anchors(now, point)
            anchorKey = key
        }
        val blend = SolarGradientClock.blend(now, anchors, SolarClock.position(now, point))
        return GradientInterpolation.frame(blend) { gradient(it, type) }
    }

    fun imageFile(phase: SkyPhase): File? {
        val name = prefs.getString("image_${phase.name}", null) ?: return null
        if (!name.matches(Regex("[A-Za-z0-9_-]+\\.jpg"))) return null
        return File(directory, name).takeIf(File::isFile)
    }

    fun useBuiltIn(phase: SkyPhase) = withConfiguration {
        val previous = imageFile(phase)
        prefs.edit().remove("image_${phase.name}").apply()
        previous?.delete()
    }

    suspend fun importImage(phase: SkyPhase, uri: Uri) = withContext(Dispatchers.IO) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val scale = min(1.0, min(4096.0 / maxOf(width, height), sqrt(8_000_000.0 / (width.toDouble() * height))))
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
        directory.mkdirs()
        val destination = File(directory, "${phase.name}_${UUID.randomUUID()}.jpg")
        try {
            destination.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) throw IOException("Image encoding failed") }
            withConfiguration {
                val previous = imageFile(phase)
                if (!prefs.edit().putString("image_${phase.name}", destination.name).commit()) throw IOException("Image preference commit failed")
                previous?.delete()
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally { bitmap.recycle() }
    }

    internal fun configuration() = WallpaperConfiguration(mode, type, prefs.getBoolean("automatic", true),
        SkyPhase.entries.getOrElse(prefs.getInt("phase", 1)) { SkyPhase.DAY },
        GradientType.entries.associateWith { type -> SkyPhase.entries.associateWith { gradient(it, type) } },
        SkyPhase.entries.filter { imageFile(it) != null }.toSet())

    internal fun replaceConfiguration(value: WallpaperConfiguration, images: Map<SkyPhase, File>) = withConfiguration {
        require(value.images == images.keys)
        val oldImages = SkyPhase.entries.mapNotNull(::imageFile)
        val previous = prefs.all
        val created = mutableListOf<File>()
        var committed = false
        try {
            check(directory.isDirectory || directory.mkdirs())
            val names = images.mapValues { (phase, source) ->
                File(directory, "${phase.name}_${UUID.randomUUID()}.jpg").also { destination ->
                    created += destination
                    source.copyTo(destination)
                }.name
            }
            val edit = prefs.edit().putString("wallpaper_mode", value.mode.name).putString("gradient_type", value.type.name)
                .putBoolean("automatic", value.automatic).putInt("phase", value.phase.ordinal)
            value.gradients.forEach { (type, phases) -> phases.forEach { (phase, gradient) -> edit.putString(gradientKey(phase, type), GradientCodec.encode(gradient)) } }
            SkyPhase.entries.forEach { phase ->
                names[phase]?.let { edit.putString("image_${phase.name}", it) } ?: edit.remove("image_${phase.name}")
            }
            if (!edit.commit()) {
                // SharedPreferences updates memory even when its disk write fails. Restore all affected
                // values before reporting failure; retain files if the rollback itself cannot persist.
                val rollback = prefs.edit()
                val keys = listOf("wallpaper_mode", "gradient_type", "automatic", "phase") + SkyPhase.entries.map { "image_${it.name}" } +
                    GradientType.entries.flatMap { type -> SkyPhase.entries.map { gradientKey(it, type) } }
                keys.forEach { key -> when (val old = previous[key]) {
                    is String -> rollback.putString(key, old)
                    is Boolean -> rollback.putBoolean(key, old)
                    is Int -> rollback.putInt(key, old)
                    else -> rollback.remove(key)
                } }
                if (!rollback.commit()) committed = true
                throw IOException("Configuration commit failed")
            }
            committed = true
            oldImages.forEach { it.delete() }
        } finally {
            if (!committed) created.forEach { it.delete() }
        }
    }
}
