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
            val preset = SkyGradient.default(SkyPhase.DAWN).copy(center = GradientPoint(.3f, .7f), radius = .6f)
            store.saveGradient(SkyPhase.DAWN, preset)
            store.setMode(WallpaperMode.GRADIENT)
            store.setType(GradientType.RADIAL)
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
}
