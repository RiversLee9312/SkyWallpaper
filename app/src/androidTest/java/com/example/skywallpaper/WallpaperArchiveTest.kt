package com.example.skywallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class WallpaperArchiveTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = context.createDeviceProtectedStorageContext()
    private val sourcePrefs get() = device.getSharedPreferences("archive_source_test", 0)
    private val targetPrefs get() = device.getSharedPreferences("archive_target_test", 0)
    private lateinit var source: WallpaperStore
    private lateinit var target: WallpaperStore
    private lateinit var archive: WallpaperArchive
    private val fixtures = mutableListOf<File>()

    @Before fun prepare() {
        sourcePrefs.edit().clear().commit(); targetPrefs.edit().clear().commit()
        source = WallpaperStore(context, sourcePrefs)
        target = WallpaperStore(context, targetPrefs)
        archive = WallpaperArchive(context, target)
    }

    @After fun cleanup() {
        SkyPhase.entries.forEach { source.useBuiltIn(it); target.useBuiltIn(it) }
        fixtures.forEach { it.delete() }
        device.deleteSharedPreferences("archive_source_test"); device.deleteSharedPreferences("archive_target_test")
    }

    private fun jpeg(color: Int): File {
        val file = File.createTempFile("archive_fixture", ".jpg", context.cacheDir).also { fixtures += it }
        Bitmap.createBitmap(48, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(color)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            bitmap.recycle()
        }
        return file
    }
    private fun exported() = ByteArrayOutputStream().also { WallpaperArchive(context, source).write(it) }.toByteArray()
    private fun zip(config: String, entries: Map<String, ByteArray> = emptyMap()): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            (mapOf("config.json" to config.toByteArray()) + entries).forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
    }.toByteArray()
    private fun rejected(bytes: ByteArray) {
        val before = targetPrefs.all.toMap()
        val oldFiles = SkyPhase.entries.mapNotNull(target::imageFile).associateWith { it.readBytes() }
        try {
            val id = archive.prepare(bytes.inputStream())
            archive.discard(id)
            fail("Invalid package was accepted")
        } catch (_: Exception) { }
        assertEquals(before, targetPrefs.all)
        oldFiles.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
    }

    @Test fun zipRoundTripRestoresAllProfilesPicturesAndBehaviorWithoutDeviceLocation() = runBlocking {
        source.setMode(WallpaperMode.GRADIENT); source.setType(GradientType.SWEEP)
        sourcePrefs.edit().putBoolean("automatic", false).putInt("phase", SkyPhase.DUSK.ordinal)
            .putLong("latitude", 31.23.toBits()).putLong("longitude", 121.47.toBits()).commit()
        GradientType.entries.forEach { type -> SkyPhase.entries.forEach { phase ->
            val id = type.ordinal * 4 + phase.ordinal
            source.saveGradient(phase, SkyGradient.default(phase, type).copy(radius = if (type == GradientType.RADIAL && phase == SkyPhase.NIGHT) 4f else .25f + id * .1f,
                center = GradientPoint(.1f + id * .03f, .9f - id * .04f)), type)
        } }
        val dawn = jpeg(0xFF3478C8.toInt()); val night = jpeg(0xFF7144A8.toInt())
        source.importImage(SkyPhase.DAWN, Uri.fromFile(dawn)); source.importImage(SkyPhase.NIGHT, Uri.fromFile(night))
        target.importImage(SkyPhase.DAY, Uri.fromFile(dawn))
        val replaced = target.imageFile(SkyPhase.DAY)!!
        targetPrefs.edit().putLong("latitude", 10.0.toBits()).putBoolean("manual_location", true).putString("unrelated", "preserved").commit()
        val bytes = exported()
        val id = archive.prepare(bytes.inputStream())
        assertEquals(WallpaperMode.IMAGE, target.mode)
        assertTrue(replaced.exists()) // validation and confirmation do not change live data
        archive.applyPrepared(id)
        val reopened = WallpaperStore(device, targetPrefs)
        assertEquals(source.configuration(), reopened.configuration())
        assertFalse(replaced.exists())
        assertNull(reopened.imageFile(SkyPhase.DAY)); assertNull(reopened.imageFile(SkyPhase.DUSK))
        for (phase in listOf(SkyPhase.DAWN, SkyPhase.NIGHT)) {
            assertArrayEquals(source.imageFile(phase)!!.readBytes(), reopened.imageFile(phase)!!.readBytes())
            assertTrue(reopened.imageFile(phase)!!.canonicalPath.startsWith(device.filesDir.canonicalPath + File.separator))
            BitmapFactory.decodeFile(reopened.imageFile(phase)!!.path).also { assertEquals(96, it.height); it.recycle() }
        }
        assertEquals(10.0.toBits(), targetPrefs.getLong("latitude", 0))
        assertTrue(targetPrefs.getBoolean("manual_location", false))
        assertEquals("preserved", targetPrefs.getString("unrelated", null))
        val reexport = ByteArrayOutputStream().also { WallpaperArchive(device, reopened).write(it) }.toByteArray()
        assertTrue(reexport.isNotEmpty())
    }

    @Test fun builtInOnlyPackageNeedsNoImagesAndCancellingKeepsEverything() = runBlocking {
        target.importImage(SkyPhase.DAY, Uri.fromFile(jpeg(0xFF3478C8.toInt())))
        val old = target.imageFile(SkyPhase.DAY)!!
        val before = targetPrefs.all.toMap()
        val bytes = exported()
        val cancelled = archive.prepare(bytes.inputStream())
        archive.discard(cancelled)
        assertEquals(before, targetPrefs.all); assertTrue(old.exists())
        val confirmed = archive.prepare(bytes.inputStream())
        archive.applyPrepared(confirmed)
        assertEquals(source.configuration(), target.configuration())
        assertFalse(old.exists())
    }

    @Test fun malformedUnsupportedAndIncompleteManifestsNeverChangeLiveData() = runBlocking {
        target.importImage(SkyPhase.DAY, Uri.fromFile(jpeg(0xFF3478C8.toInt())))
        val valid = WallpaperConfigurationCodec.encode(source.configuration())
        val modifications: List<(JSONObject) -> Unit> = listOf(
            { it.put("version", 2) }, { it.put("version", 1.5) }, { it.put("format", "other") },
            { it.put("mode", "UNKNOWN") }, { it.put("type", "UNKNOWN") }, { it.put("automatic", "true") },
            { it.getJSONObject("gradients").remove("RADIAL") },
            { it.getJSONObject("gradients").getJSONObject("SWEEP").remove("NIGHT") },
            { it.getJSONObject("gradients").getJSONObject("RADIAL").getJSONObject("DAWN").put("radius", 4.01) },
            { it.getJSONObject("gradients").getJSONObject("LINEAR").getJSONObject("DAWN").getJSONArray("start").put(0, 2) },
            { it.getJSONObject("images").put("DAWN", "../../outside.jpg") },
            { it.getJSONObject("images").put("DAWN", "images/DAWN.jpg") },
        )
        modifications.forEach { change -> rejected(zip(JSONObject(valid).also(change).toString())) }
        rejected(zip("not json"))
        rejected(exported().dropLast(30).toByteArray())
        rejected(ByteArray(0))
    }

    @Test fun unsafePathsExtraFilesOversizedEntriesAndInvalidPicturesAreRejected() {
        val valid = WallpaperConfigurationCodec.encode(source.configuration())
        listOf("../outside.jpg", "/outside.jpg", "images/../../outside.jpg", "images\\DAWN.jpg", "extra.txt")
            .forEach { rejected(zip(valid, mapOf(it to byteArrayOf(1)))) }
        rejected(zip(valid, mapOf("images/DAWN.jpg" to byteArrayOf(1))))
        val withImage = JSONObject(valid).apply { getJSONObject("images").put("DAWN", "images/DAWN.jpg") }.toString()
        rejected(zip(withImage, mapOf("images/DAWN.jpg" to "not a picture".toByteArray())))
        rejected(zip(withImage, mapOf("images/DAWN.jpg" to ByteArray(24 * 1024 * 1024 + 1))))
        rejected(zip(" ".repeat(128 * 1024 + 1)))
        val duplicate = zip(valid, mapOf("images/DAWN.jpg" to byteArrayOf(1), "images/DUSK.jpg" to byteArrayOf(2)))
            .toString(Charsets.ISO_8859_1).replace("images/DUSK.jpg", "images/DAWN.jpg").toByteArray(Charsets.ISO_8859_1)
        rejected(duplicate)
    }

    @Test fun uriExportAndImportUseTheSamePortableFormat() = runBlocking {
        val file = File.createTempFile("archive_uri", ".zip", context.cacheDir).also { fixtures += it }
        WallpaperArchive(context, source).export(Uri.fromFile(file))
        assertTrue(file.length() > 0)
        val id = archive.prepare(Uri.fromFile(file))
        archive.apply(id)
        assertEquals(source.configuration(), target.configuration())
    }

    @Test fun pendingImportRestoresFromSavedStateAndOnlyConfirmationChangesConfiguration() {
        source.setMode(WallpaperMode.GRADIENT); source.setType(GradientType.RADIAL)
        val file = File.createTempFile("archive_pending", ".zip", context.cacheDir).also { fixtures += it }
        file.writeBytes(exported())
        val saved = androidx.lifecycle.SavedStateHandle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var first: WallpaperTransferModel
        instrumentation.runOnMainSync {
            first = WallpaperTransferModel(context, target, saved)
            first.prepare(Uri.fromFile(file))
        }
        fun waitUntil(done: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (true) {
                var complete = false
                instrumentation.runOnMainSync { complete = done() }
                if (complete) return
                check(System.nanoTime() < deadline) { "Transfer did not finish" }
                Thread.sleep(20)
            }
        }
        waitUntil { !first.busy }
        assertNotNull(first.pending)
        assertEquals(WallpaperMode.IMAGE, target.mode)
        lateinit var restored: WallpaperTransferModel
        instrumentation.runOnMainSync {
            restored = WallpaperTransferModel(context, target, saved)
            assertEquals(first.pending, restored.pending)
            restored.confirm()
        }
        waitUntil { !restored.busy }
        assertEquals(source.configuration(), target.configuration())
        assertNull(restored.pending)
        assertEquals(R.string.config_imported, restored.message)
        assertEquals(1, restored.importCount)
    }
}
