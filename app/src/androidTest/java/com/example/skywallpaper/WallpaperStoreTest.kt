package com.example.skywallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WallpaperStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun bundledDefaultsMatchEveryExportedDeviceProfileWithoutOverwritingSavedSettings() {
        val exported = org.json.JSONObject(context.assets.open("default_gradients.json").bufferedReader().use { it.readText() })
        val device = context.createDeviceProtectedStorageContext()
        val name = "bundled_gradient_defaults_test"
        val prefs = device.getSharedPreferences(name, 0)
        prefs.edit().clear().commit()
        try {
            val store = WallpaperStore(context, prefs)
            GradientType.entries.forEach { type -> SkyPhase.entries.forEach { phase ->
                val expected = GradientCodec.decode(exported.getJSONObject(type.name).getJSONObject(phase.name).toString())
                assertEquals(expected, SkyGradient.default(phase, type))
                assertEquals(expected, store.gradient(phase, type))
            } }
            val custom = store.gradient(SkyPhase.DAY, GradientType.RADIAL).copy(radius = .67f)
            store.saveGradient(SkyPhase.DAY, custom, GradientType.RADIAL)
            val reopened = WallpaperStore(device, prefs)
            assertEquals(custom, reopened.gradient(SkyPhase.DAY, GradientType.RADIAL))
            assertEquals(SkyGradient.default(SkyPhase.DAY, GradientType.SWEEP), reopened.gradient(SkyPhase.DAY, GradientType.SWEEP))
        } finally { device.deleteSharedPreferences(name) }
    }

    @Test fun importedImagesPersistBeforeUnlockAndRestoreIndependently() = runBlocking {
        val name = "wallpaper_import_test"
        val device = context.createDeviceProtectedStorageContext()
        val prefs = device.getSharedPreferences(name, 0)
        prefs.edit().clear().commit()
        val store = WallpaperStore(context, prefs)
        val fixture = File(context.cacheDir, "wallpaper_fixture.png")
        val bitmap = Bitmap.createBitmap(48, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3478C8.toInt())
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            store.importImage(SkyPhase.DAWN, Uri.fromFile(fixture))
            store.importImage(SkyPhase.NIGHT, Uri.fromFile(fixture))
            val file = store.imageFile(SkyPhase.DAWN)!!
            assertTrue(file.canonicalPath.startsWith(device.filesDir.canonicalPath + File.separator))
            assertTrue(file.length() > 0)
            assertNotNull(WallpaperStore(device, prefs).imageFile(SkyPhase.DAWN))
            store.useBuiltIn(SkyPhase.DAWN)
            assertNull(store.imageFile(SkyPhase.DAWN))
            assertFalse(file.exists())
            assertNotNull(store.imageFile(SkyPhase.NIGHT))
        } finally {
            SkyPhase.entries.forEach(store::useBuiltIn)
            fixture.delete(); device.deleteSharedPreferences(name)
        }
    }

    @Test fun everyGradientRendersAndSettingsSurviveRecreation() {
        val device = context.createDeviceProtectedStorageContext()
        val name = "gradient_store_test"
        val prefs = device.getSharedPreferences(name, 0)
        prefs.edit().clear().commit()
        try {
            val store = WallpaperStore(context, prefs)
            val preset = SkyGradient.default(SkyPhase.DAWN).copy(center = GradientPoint(.3f, .7f), radius = 4f)
            store.setType(GradientType.RADIAL)
            store.saveGradient(SkyPhase.DAWN, preset)
            store.setMode(WallpaperMode.GRADIENT)
            val reopened = WallpaperStore(device, prefs)
            assertEquals(preset, reopened.gradient(SkyPhase.DAWN))
            assertEquals(WallpaperMode.GRADIENT, reopened.mode)
            assertEquals(GradientType.RADIAL, reopened.type)
            val bitmap = Bitmap.createBitmap(120, 240, Bitmap.Config.ARGB_8888)
            try {
                GradientType.entries.forEach { type ->
                    val canvas = Canvas(bitmap)
                    val frame = GradientFrame.from(preset)
                    canvas.drawRect(0f, 0f, 120f, 240f, GradientRenderer.paint(type, frame, 120f, 240f))
                    assertEquals(255, bitmap.getPixel(20, 40) ushr 24)
                    assertNotEquals(bitmap.getPixel(10, 10), bitmap.getPixel(80, 180))
                }
                // Interpolated line endpoints may meet; it must still produce a valid shader.
                val zeroLine = preset.copy(start = GradientPoint(.5f, .5f), end = GradientPoint(.5f, .5f))
                assertNotNull(GradientRenderer.shader(GradientType.LINEAR, GradientFrame.from(zeroLine), 120f, 240f))
            } finally { bitmap.recycle() }
            assertEquals(GradientFrame.from(preset), reopened.frame(Instant.now(), null, false, SkyPhase.DAWN))
        } finally { device.deleteSharedPreferences(name) }
    }

    @Test fun allTwelveProfilesKeepTheirOwnStopsAndGeometryAcrossSwitchesAndRecreation() {
        val device = context.createDeviceProtectedStorageContext()
        val name = "gradient_isolation_test"
        val prefs = device.getSharedPreferences(name, 0)
        prefs.edit().clear().commit()
        try {
            val expected = GradientType.entries.associateWith { type -> SkyPhase.entries.associateWith { phase ->
                val index = type.ordinal * 4 + phase.ordinal
                SkyGradient.default(phase, type).copy(
                    stops = listOf(GradientStop(0xFF000000.toInt() or (0x10203 * (index + 1)), .02f * index),
                        GradientStop(0xFFEEDDCC.toInt() - index, 1f)),
                    start = GradientPoint(.03f * index, .01f * index), end = GradientPoint(1f - .02f * index, 1f),
                    center = GradientPoint(.02f * index, .9f - .03f * index), radius = .2f + .1f * index)
            } }
            val store = WallpaperStore(context, prefs)
            expected.forEach { (type, phases) ->
                store.setType(type)
                phases.forEach { (phase, value) -> store.saveGradient(phase, value, type) }
            }
            repeat(3) {
                GradientType.entries.reversed().forEach { type ->
                    store.setType(type)
                    val recreated = WallpaperStore(device, prefs)
                    expected.forEach { (kind, phases) -> phases.forEach { (phase, value) ->
                        assertEquals(value, recreated.gradient(phase, kind))
                    } }
                    SkyPhase.entries.forEach { phase ->
                        assertEquals(GradientFrame.from(expected.getValue(type).getValue(phase)), recreated.frame(Instant.now(), null, false, phase))
                    }
                }
            }
            store.saveGradient(SkyPhase.DAWN, SkyGradient.default(SkyPhase.DAWN, GradientType.RADIAL), GradientType.RADIAL)
            assertEquals(expected.getValue(GradientType.LINEAR).getValue(SkyPhase.DAWN), store.gradient(SkyPhase.DAWN, GradientType.LINEAR))
            assertEquals(expected.getValue(GradientType.SWEEP).getValue(SkyPhase.DAWN), store.gradient(SkyPhase.DAWN, GradientType.SWEEP))
        } finally { device.deleteSharedPreferences(name) }
    }

    @Test fun legacyMigrationPreservesSurvivingTypeAndRawBackupWithoutCopyingToOtherTypes() {
        val device = context.createDeviceProtectedStorageContext()
        val name = "gradient_legacy_test"
        val prefs = device.getSharedPreferences(name, 0)
        val legacy = SkyGradient.default(SkyPhase.DAWN).copy(radius = .42f)
        val existing = SkyGradient.default(SkyPhase.NIGHT).copy(radius = .88f)
        val raw = GradientCodec.encode(legacy)
        prefs.edit().clear().putString("gradient_type", "RADIAL").putString("gradient_DAWN", raw)
            .putString("gradient_NIGHT", raw).putString("gradient_DAY", "broken legacy data")
            .putString(WallpaperStore.gradientKey(SkyPhase.NIGHT, GradientType.RADIAL), GradientCodec.encode(existing)).commit()
        try {
            val locked = object : android.content.ContextWrapper(device) {
                override fun createPackageContext(packageName: String, flags: Int): android.content.Context =
                    error("Gradient migration must not access credential protected storage")
            }
            val store = WallpaperStore(locked, prefs)
            assertEquals(legacy, store.gradient(SkyPhase.DAWN, GradientType.RADIAL))
            assertEquals(existing, store.gradient(SkyPhase.NIGHT, GradientType.RADIAL))
            assertEquals(SkyGradient.default(SkyPhase.DAY, GradientType.RADIAL), store.gradient(SkyPhase.DAY, GradientType.RADIAL))
            assertEquals(raw, prefs.getString("gradient_DAWN", null))
            assertEquals("broken legacy data", prefs.getString("gradient_DAY", null))
            GradientType.entries.filter { it != GradientType.RADIAL }.forEach { type ->
                store.setType(type)
                val reopened = WallpaperStore(locked, prefs)
                SkyPhase.entries.forEach { assertEquals(SkyGradient.default(it, type), reopened.gradient(it)) }
                assertEquals(legacy, reopened.gradient(SkyPhase.DAWN, GradientType.RADIAL))
            }
        } finally { device.deleteSharedPreferences(name) }
    }
}
