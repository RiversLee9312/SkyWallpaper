package com.example.skywallpaper

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SkyDeviceTest {
    @Rule @JvmField val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var original: Map<String, *> = emptyMap<String, Any>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun prepare() {
        val prefs = SkyPreferenceStorage.open(context)
        original = prefs.all.toMap()
        prefs.edit().clear().commit()
        SkyRepository(context).savePoint(GeoPoint(39.90, 116.40), manual = true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun restore() {
        scenario?.close()
        val edit = SkyPreferenceStorage.open(context).edit().clear()
        original.forEach { (key, value) -> when (value) {
            is Long -> edit.putLong(key, value)
            is Int -> edit.putInt(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is String -> edit.putString(key, value)
            is Float -> edit.putFloat(key, value)
        } }
        edit.commit()
    }

    @Test fun automaticPreviewUsesSolarPositionAndGalleryOnlyChangesPreview() {
        val phase = SolarClock.phase(Instant.now(), GeoPoint(39.90, 116.40))
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(phase.titleRes))).assertExists()
        val other = SkyPhase.entries.first { it != phase }
        compose.onNodeWithContentDescription(context.getString(R.string.gallery_description, context.getString(other.titleRes))).performClick().assertIsSelected()
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(other.titleRes))).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.reset_preview)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.gallery_description, context.getString(phase.titleRes))).assertIsSelected()
        Assert.assertTrue(SkyRepository(context).automatic)
        compose.onNodeWithText(context.getString(R.string.utc_label)).performScrollTo().assertIsDisplayed()
    }

    @Test fun fixedWallpaperChoicePersistsAcrossActivityRecreation() {
        compose.onNodeWithContentDescription(context.getString(R.string.gallery_description, context.getString(R.string.phase_dawn))).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.fix_preview, context.getString(R.string.phase_dawn))).performClick()
        Assert.assertFalse(SkyRepository(context).automatic)
        Assert.assertEquals(SkyPhase.DAWN, SkyRepository(context).manualPhase)
        scenario?.recreate()
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(R.string.phase_dawn))).assertExists()
    }

    @Test fun manifestRequestsGpsAndBackgroundWithApproximateFallback() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        Assert.assertTrue(permissions.contains("android.permission.ACCESS_COARSE_LOCATION"))
        Assert.assertTrue(permissions.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
        Assert.assertTrue(permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
    }

    @Test fun settingsSwitchWorksFromTheWholeRowAndBackReturnsToGallery() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsOn().performClick()
        Assert.assertFalse(SkyRepository(context).automatic)
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsOff()
        scenario?.recreate()
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsOff().performClick()
        Assert.assertTrue(SkyRepository(context).automatic)
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.gallery_description, context.getString(R.string.phase_dawn))).assertIsDisplayed()
    }

    @Test fun predictiveBackPreviewsHomeCanCancelAndThenCommitWithoutLosingPreview() {
        val preview = context.getString(R.string.gallery_description, context.getString(R.string.phase_dusk))
        compose.onNodeWithContentDescription(preview).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.waitForIdle()
        fun startAndProgress() {
            scenario?.onActivity { activity ->
                activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 600f, 0f, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
            scenario?.onActivity { activity ->
                activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 600f, .6f, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
        }
        startAndProgress()
        compose.onNodeWithContentDescription(preview).assertExists()
        scenario?.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsDisplayed()
        compose.onNodeWithContentDescription(preview).assertDoesNotExist()
        startAndProgress()
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(R.string.phase_dusk))).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertDoesNotExist()
        Assert.assertTrue(SkyRepository(context).automatic)
    }

    @Test fun adaptiveIconHasDedicatedMonochromeLayerAndEveryLocaleHasTranslatedResources() {
        val icon = context.packageManager.getApplicationIcon(context.packageName) as AdaptiveIconDrawable
        Assert.assertNotNull(icon.monochrome)
        for ((locale, title) in listOf("en" to "Dawn", "zh-Hans" to "清晨", "zh-Hant" to "清晨")) {
            val configuration = android.content.res.Configuration(context.resources.configuration)
            configuration.setLocale(java.util.Locale.forLanguageTag(locale))
            val localized = context.createConfigurationContext(configuration)
            Assert.assertEquals(title, localized.getString(R.string.phase_dawn))
            Assert.assertTrue(localized.getString(R.string.headline).isNotBlank())
        }
    }

    @Test fun supportedLanguagesResolveAndFormatSettingsIncludingRtl() {
        val supported = android.app.LocaleConfig(context).supportedLocales!!
        val languages = listOf("en", "zh-Hans", "zh-Hant", "es", "fr", "de", "it", "pt-BR", "ja", "ko", "ru", "ar", "hi", "id", "tr",
            "nl", "pl", "uk", "vi", "th", "bn")
        val english = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.ENGLISH)
        })
        Assert.assertEquals(languages.size, supported.size())
        for (tag in languages) {
            val locale = java.util.Locale.forLanguageTag(tag)
            Assert.assertTrue("Missing system app language: $tag", (0 until supported.size()).any { supported[it] == locale })
            val configuration = android.content.res.Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            val localized = context.createConfigurationContext(configuration)
            val phase = localized.getString(R.string.phase_day)
            Assert.assertTrue(localized.getString(R.string.fix_preview, phase).contains(phase))
            Assert.assertTrue(localized.getString(R.string.preview_status, phase, phase).contains(phase))
            Assert.assertTrue(localized.getString(R.string.permission_format, "test", "precision").contains("test"))
            Assert.assertTrue(localized.getString(R.string.updated_format, "10-05 00:28").contains("10-05 00:28"))
            Assert.assertEquals("42%", localized.getString(R.string.percent_format, 42).let { value ->
                // Formatting may use the locale's digit shapes, but it must preserve its numeric value.
                value.map { char -> Character.digit(char, 10).takeIf { it >= 0 }?.let { ('0'.code + it).toChar() } ?: char }.joinToString("")
            })
            Assert.assertEquals(if (tag == "ar") android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR,
                localized.resources.configuration.layoutDirection)
            if (tag != "en") Assert.assertNotEquals("Language $tag fell back to English", "Settings", localized.getString(R.string.settings))
            if (tag in setOf("nl", "pl", "uk", "vi", "th", "bn")) {
                for (key in listOf(R.string.headline, R.string.gradient_type, R.string.choose_image, R.string.manual_location_hint, R.string.gradient_hint)) {
                    Assert.assertNotEquals("Missing localized editor copy for $tag/$key", english.getString(key), localized.getString(key))
                }
            }
        }
    }

    @Test fun gradientEditorSavesColorsAndGlobalTypeAcrossRecreation() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.wallpaper_content)).performClick()
        compose.onNodeWithText(context.getString(R.string.gradient_mode)).performClick()
        Assert.assertEquals(WallpaperMode.GRADIENT, SkyRepository(context).wallpapers.mode)
        compose.onNodeWithText(context.getString(R.string.gradient_type)).performClick()
        compose.onNodeWithText(context.getString(R.string.gradient_radial)).performClick()
        compose.onNodeWithText(context.getString(R.string.phase_dawn)).performClick()
        compose.onNodeWithText(context.getString(R.string.color_stop, 1)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("123456")
        compose.onNode(hasText(context.getString(R.string.save_location)) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        scenario?.recreate()
        val store = SkyRepository(context).wallpapers
        Assert.assertEquals(GradientType.RADIAL, store.type)
        Assert.assertEquals(0xFF123456.toInt(), store.gradient(SkyPhase.DAWN).stops.first().color)
        compose.onNodeWithText(context.getString(R.string.picture_mode)).performClick()
        Assert.assertEquals(WallpaperMode.IMAGE, SkyRepository(context).wallpapers.mode)
    }

    @Test fun changingPhoneTimezoneKeepsUtcSunriseUnchanged() {
        val originalZone = java.util.TimeZone.getDefault().id
        val now = Instant.now()
        val sunrise = SolarClock.events(now, GeoPoint(39.90, 116.40)).first { it.type == SolarEventType.SUNRISE }
        val utc = java.time.ZoneOffset.UTC
        val pattern = if (sunrise.instant.atOffset(utc).toLocalDate() == now.atOffset(utc).toLocalDate()) "HH:mm" else "MM-dd HH:mm"
        val time = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(utc).format(sunrise.instant)
        fun setZone(zone: String) {
            val command = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd alarm set-timezone $zone")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
            scenario?.recreate()
            compose.onNodeWithText(time).performScrollTo().assertIsDisplayed()
        }
        try {
            setZone("Asia/Shanghai")
            setZone("Pacific/Honolulu")
        } finally { setZone(originalZone) }
    }
}
