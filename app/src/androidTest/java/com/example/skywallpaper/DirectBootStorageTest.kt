package com.example.skywallpaper

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectBootStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migrationPreservesExistingSkyAndNeverOverwritesItAgain() {
        val name = "sky_migration_test"
        val credential = context
        val device = context.createDeviceProtectedStorageContext()
        credential.deleteSharedPreferences(name)
        device.deleteSharedPreferences(name)
        try {
            val point = GeoPoint(31.23, 121.47)
            credential.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .putLong("latitude", point.latitude.toBits()).putLong("longitude", point.longitude.toBits())
                .putLong("updated", 1_791_158_400_000L).putBoolean("manual_location", true)
                .putBoolean("automatic", false).putInt("phase", SkyPhase.DUSK.ordinal)
                .putString("unrelated_private_data", "should not migrate").commit()
            val prefs = SkyPreferenceStorage.open(context, name)
            assertEquals(point.latitude.toBits(), prefs.getLong("latitude", 0))
            assertEquals(point.longitude.toBits(), prefs.getLong("longitude", 0))
            assertEquals(1_791_158_400_000L, prefs.getLong("updated", 0))
            assertTrue(prefs.getBoolean("manual_location", false))
            assertFalse(prefs.getBoolean("automatic", true))
            assertEquals(SkyPhase.DUSK.ordinal, prefs.getInt("phase", -1))
            assertFalse(prefs.contains("unrelated_private_data"))
            assertFalse(credential.getSharedPreferences(name, Context.MODE_PRIVATE).contains("latitude"))
            prefs.edit().putInt("phase", SkyPhase.NIGHT.ordinal).commit()
            credential.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putInt("phase", SkyPhase.DAY.ordinal).commit()
            assertEquals(SkyPhase.NIGHT.ordinal, SkyPreferenceStorage.open(context, name).getInt("phase", -1))
        } finally {
            credential.deleteSharedPreferences(name)
            device.deleteSharedPreferences(name)
        }
    }

    @Test fun lockedAccessSkipsCredentialStorageAndUnlockUpdatesExistingReaders() {
        val name = "sky_locked_test"
        val device = context.createDeviceProtectedStorageContext()
        val credential = context
        device.deleteSharedPreferences(name)
        credential.deleteSharedPreferences(name)
        val locked = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = error("Default storage accessed before unlock")
        }
        try {
            val beforeUnlock = SkyPreferenceStorage.open(locked, name, unlocked = false)
            assertFalse(beforeUnlock.contains("direct_boot_migrated"))
            credential.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
                .putInt("phase", SkyPhase.DAWN.ordinal).putBoolean("automatic", false).commit()
            var notified = false
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> notified = true }
            beforeUnlock.registerOnSharedPreferenceChangeListener(listener)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { SkyPreferenceStorage.open(context, name) }
            assertEquals(SkyPhase.DAWN.ordinal, beforeUnlock.getInt("phase", -1))
            assertTrue(notified)
            beforeUnlock.unregisterOnSharedPreferenceChangeListener(listener)
            // Subsequent BFU reads use the exact saved settings, with no credential access.
            assertFalse(SkyPreferenceStorage.open(locked, name, unlocked = false).getBoolean("automatic", true))
        } finally {
            device.deleteSharedPreferences(name)
            credential.deleteSharedPreferences(name)
        }
    }

    @Test fun wallpaperIsDirectBootAwareAndAllImagesArePackagedResources() {
        val info = context.packageManager.getServiceInfo(ComponentName(context, SkyWallpaperService::class.java), 0)
        assertTrue(info.directBootAware)
        SkyPhase.entries.forEach { phase ->
            val bitmap = BitmapFactory.decodeResource(context.resources, phase.imageRes)
            assertNotNull(bitmap)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
        }
    }
}
