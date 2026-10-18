package com.example.skywallpaper

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal data class WallpaperConfiguration(
    val mode: WallpaperMode,
    val type: GradientType,
    val automatic: Boolean,
    val phase: SkyPhase,
    val gradients: Map<GradientType, Map<SkyPhase, SkyGradient>>,
    val images: Set<SkyPhase>,
)

/** Portable wallpaper settings. Device location, language and permission grants stay on the device. */
internal object WallpaperConfigurationCodec {
    fun imagePath(phase: SkyPhase) = "images/${phase.name}.jpg"

    fun encode(value: WallpaperConfiguration): String = JSONObject().apply {
        put("format", "skywallpaper")
        put("version", 1)
        put("mode", value.mode.name)
        put("type", value.type.name)
        put("automatic", value.automatic)
        put("phase", value.phase.name)
        put("gradients", JSONObject().apply { GradientType.entries.forEach { type ->
            put(type.name, JSONObject().apply { SkyPhase.entries.forEach { phase ->
                put(phase.name, JSONObject(GradientCodec.encode(value.gradients.getValue(type).getValue(phase))))
            } })
        } })
        put("images", JSONObject().apply { value.images.forEach { put(it.name, imagePath(it)) } })
    }.toString(2)

    fun decode(source: String): WallpaperConfiguration {
        val json = JSONObject(source)
        require(json.getString("format") == "skywallpaper" && json.get("version") == 1)
        val mode = WallpaperMode.valueOf(json.getString("mode"))
        val type = GradientType.valueOf(json.getString("type"))
        require(json.get("automatic") is Boolean)
        val automatic = json.getBoolean("automatic")
        val phase = SkyPhase.valueOf(json.getString("phase"))
        val gradients = json.getJSONObject("gradients")
        require(gradients.keys().asSequence().toSet() == GradientType.entries.map { it.name }.toSet())
        val profiles = GradientType.entries.associateWith { kind ->
            val phases = gradients.getJSONObject(kind.name)
            require(phases.keys().asSequence().toSet() == SkyPhase.entries.map { it.name }.toSet())
            SkyPhase.entries.associateWith { GradientCodec.decode(phases.getJSONObject(it.name).toString()) }
        }
        val images = json.getJSONObject("images")
        val custom = images.keys().asSequence().map { name ->
            SkyPhase.valueOf(name).also { require(images.getString(name) == imagePath(it)) }
        }.toSet()
        return WallpaperConfiguration(mode, type, automatic, phase, profiles, custom)
    }
}

/** Validate the entire archive in private staging storage before changing any live configuration. */
internal class WallpaperArchive(private val context: Context, private val store: WallpaperStore) {
    private val root get() = File(context.cacheDir, "wallpaper-transfer").apply { mkdirs() }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val temporary = File(root, "export_${UUID.randomUUID()}.zip")
        try {
            temporary.outputStream().buffered().use(::write)
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open destination")
            output.use { destination -> temporary.inputStream().use { it.copyTo(destination) } }
        } finally { temporary.delete() }
    }

    internal fun write(output: OutputStream) = store.withConfiguration {
        val configuration = store.configuration()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, body: () -> Unit) {
                zip.putNextEntry(ZipEntry(name)); body(); zip.closeEntry()
            }
            entry("config.json") { zip.write(WallpaperConfigurationCodec.encode(configuration).toByteArray(Charsets.UTF_8)) }
            configuration.images.forEach { phase ->
                val image = store.imageFile(phase) ?: throw IOException("Missing custom image")
                entry(WallpaperConfigurationCodec.imagePath(phase)) { image.inputStream().use { it.copyTo(zip) } }
            }
        }
    }

    suspend fun prepare(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open archive")
        input.use(::prepare)
    }

    internal fun prepare(input: InputStream): String {
        val id = "import_${UUID.randomUUID()}"
        val folder = File(root, id).apply { check(mkdir()) }
        try {
            val archive = File(folder, "source.zip")
            archive.outputStream().buffered().use { input.copyLimited(it, MAX_ARCHIVE) }
            val seen = mutableSetOf<String>()
            val allowed = setOf("config.json") + SkyPhase.entries.map(WallpaperConfigurationCodec::imagePath)
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(++count <= 6)
                    if (entry.name == "images/" && entry.isDirectory) {
                        require(seen.add(entry.name)); continue
                    }
                    // Only known output paths are created as ordinary files; archive attributes
                    // cannot create links. The allowlist also excludes traversal and extra files.
                    require(!entry.isDirectory && entry.name in allowed && seen.add(entry.name))
                    val limit = if (entry.name == "config.json") MAX_CONFIG else MAX_IMAGE
                    require(entry.size in 1..limit)
                    val target = File(folder, entry.name)
                    check(target.canonicalPath.startsWith(folder.canonicalPath + File.separator))
                    target.parentFile?.mkdirs()
                    val crc = CRC32()
                    val size = target.outputStream().buffered().use { output ->
                        zip.getInputStream(entry).use { it.copyLimited(output, limit, crc) }
                    }
                    require(size == entry.size && crc.value == entry.crc)
                }
            }
            val config = File(folder, "config.json")
            require(config.isFile)
            val configuration = WallpaperConfigurationCodec.decode(config.readText(Charsets.UTF_8))
            val expected = setOf("config.json") + configuration.images.map(WallpaperConfigurationCodec::imagePath)
            require(seen - "images/" == expected)
            configuration.images.forEach { validateImage(File(folder, WallpaperConfigurationCodec.imagePath(it))) }
            // Store the normalized manifest; the original ZIP is no longer needed for confirmation.
            config.writeText(WallpaperConfigurationCodec.encode(configuration), Charsets.UTF_8)
            check(archive.delete())
            return id
        } catch (error: Exception) {
            folder.deleteRecursively()
            throw error
        }
    }

    suspend fun apply(id: String) = withContext(Dispatchers.IO) { applyPrepared(id) }

    internal fun applyPrepared(id: String) {
        val folder = staging(id)
        val configuration = WallpaperConfigurationCodec.decode(File(folder, "config.json").readText(Charsets.UTF_8))
        val images = configuration.images.associateWith { phase ->
            File(folder, WallpaperConfigurationCodec.imagePath(phase)).also(::validateImage)
        }
        store.replaceConfiguration(configuration, images)
        folder.deleteRecursively()
    }

    fun discard(id: String) { staging(id).deleteRecursively() }

    private fun staging(id: String): File {
        require(id.matches(Regex("import_[0-9a-f-]{36}")))
        return File(root, id)
    }

    private fun validateImage(file: File) {
        require(file.isFile && file.length() in 1..MAX_IMAGE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 &&
            bounds.outWidth.toLong() * bounds.outHeight <= 8_000_000)
        // Bounds alone accept some truncated files. Decode fully before replacing any live images.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.recycle()
    }

    private fun InputStream.copyLimited(output: OutputStream, limit: Long, crc: CRC32? = null): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count == -1) return total
            total += count
            require(total <= limit)
            crc?.update(buffer, 0, count)
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val MAX_CONFIG = 128L * 1024
        const val MAX_IMAGE = 24L * 1024 * 1024
        const val MAX_ARCHIVE = 100L * 1024 * 1024
    }
}
