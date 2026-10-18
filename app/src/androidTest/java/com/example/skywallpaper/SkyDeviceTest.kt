package com.example.skywallpaper

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
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

    @Test fun previewTimeChangesOnlyThePreviewAndRestoresAcrossRecreation() {
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        val phase = SolarClock.phase(Instant.now(), GeoPoint(39.90, 116.40))
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(phase.titleRes))).assertExists()
        compose.onNodeWithText(context.getString(R.string.event_sunrise)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(R.string.phase_dawn))).assertExists()
        val chosen = compose.onNodeWithTag("preview-selected-time").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
        scenario?.recreate()
        Assert.assertEquals(chosen, compose.onNodeWithTag("preview-selected-time").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text])
        compose.onNodeWithContentDescription(context.getString(R.string.reset_preview)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(phase.titleRes))).assertExists()
        Assert.assertTrue(SkyRepository(context).automatic)
        Assert.assertEquals(SkyPhase.DAY, SkyRepository(context).manualPhase)
    }

    @Test fun homeHasTwoActionsAndFullscreenHidesBackAndTimeCardTogether() {
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.enable_wallpaper)).assertIsDisplayed()
        compose.onNodeWithTag("preview-time-card").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.today)).assertDoesNotExist()
        SkyPhase.entries.forEach {
            compose.onNodeWithContentDescription(context.getString(R.string.gallery_description, context.getString(it.titleRes))).assertIsDisplayed()
        }
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        val bounds = compose.onNodeWithContentDescription(context.getString(R.string.image_description,
            context.getString(SolarClock.phase(Instant.now(), GeoPoint(39.90, 116.40)).titleRes))).fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(context.resources.displayMetrics.widthPixels.toFloat(), bounds.width, 1f)
        Assert.assertEquals(context.resources.displayMetrics.heightPixels.toFloat(), bounds.height, 1f)
        compose.onNodeWithTag("preview-time-card").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.enter_fullscreen)).performClick()
        compose.onNodeWithTag("preview-time-card").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.exit_fullscreen)).assertIsDisplayed()
        scenario?.recreate()
        compose.onNodeWithContentDescription(context.getString(R.string.exit_fullscreen)).assertIsDisplayed()
        compose.onNodeWithTag("preview-time-card").assertDoesNotExist()
        scenario?.onActivity {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)!!
            Assert.assertFalse(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()))
            Assert.assertFalse(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars()))
        }
        compose.onNodeWithContentDescription(context.getString(R.string.exit_fullscreen)).performClick()
        compose.onNodeWithTag("preview-time-card").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.enter_fullscreen)).performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("preview-time-card").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).assertIsDisplayed()
        compose.waitUntil(5_000) {
            var visible = false
            scenario?.onActivity {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)!!
                visible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()) &&
                    insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
            visible
        }
    }

    @Test fun fullScreenPreviewStartsWithThePeriodSelectedInTheHomeGallery() {
        compose.onNodeWithContentDescription(context.getString(R.string.gallery_description, context.getString(R.string.phase_dawn))).performClick()
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(R.string.phase_dawn))).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.reset_preview)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.current_sky)).assertExists()
        Assert.assertTrue(SkyRepository(context).automatic)
    }

    @Test fun previewSliderRendersSavedNightGradientWithoutChangingWallpaperSettings() {
        val store = SkyRepository(context).wallpapers
        val night = SkyGradient(listOf(GradientStop(0xFF123456.toInt(), .1f), GradientStop(0xFFAC7654.toInt(), .8f)),
            start = GradientPoint(.1f, .8f), end = GradientPoint(.9f, .2f))
        store.setMode(WallpaperMode.GRADIENT)
        store.saveGradient(SkyPhase.NIGHT, night)
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        compose.onNodeWithTag("preview-time-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        val description = context.getString(R.string.image_description, context.getString(R.string.phase_night))
        compose.onNodeWithContentDescription(description).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.enter_fullscreen)).performClick()
        val bitmap = compose.onNodeWithContentDescription(description).captureToImage()
        val x = bitmap.width / 2; val y = bitmap.height / 2
        val actual = bitmap.toPixelMap()[x, y].toArgb()
        val reference = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(reference).drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
            GradientRenderer.paint(GradientType.LINEAR, GradientFrame.from(night), bitmap.width.toFloat(), bitmap.height.toFloat()))
        val expected = reference.getPixel(x, y)
        reference.recycle()
        for (shift in listOf(0, 8, 16)) Assert.assertTrue(kotlin.math.abs((actual ushr shift and 255) - (expected ushr shift and 255)) <= 2)
        Assert.assertTrue(SkyRepository(context).automatic)
        Assert.assertEquals(SkyPhase.DAY, SkyRepository(context).manualPhase)
        Assert.assertEquals(night, store.gradient(SkyPhase.NIGHT))
    }

    @Test fun previewWithoutLocationKeepsPresetsAndLocationEntryAvailable() {
        SkyPreferenceStorage.open(context).edit().remove("latitude").remove("longitude").commit()
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.gallery_description, context.getString(R.string.phase_dusk))).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.image_description, context.getString(R.string.phase_dusk))).assertExists()
        compose.onNodeWithText(context.getString(R.string.manual_coordinates)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.latitude)).assertExists()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        Assert.assertNull(SkyRepository(context).point)
        Assert.assertEquals(SkyPhase.DAY, SkyRepository(context).manualPhase)
    }

    @Test fun previewAtPoleKeepsTimeScrubbingWithoutInventingSunriseOrSunset() {
        val point = GeoPoint(90.0, 0.0)
        SkyRepository(context).savePoint(point, manual = true)
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        compose.onNodeWithTag("preview-time-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(43200f) }
        compose.onNodeWithText(context.getString(R.string.polar_notice)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.event_sunrise)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.event_sunset)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.enter_fullscreen)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.exit_fullscreen)).assertIsDisplayed()
    }

    @Test fun fixedWallpaperChoicePersistsAcrossActivityRecreation() {
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        compose.onNodeWithText(context.getString(R.string.event_sunrise)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.fix_preview, context.getString(R.string.phase_dawn))).performClick()
        Assert.assertFalse(SkyRepository(context).automatic)
        Assert.assertEquals(SkyPhase.DAWN, SkyRepository(context).manualPhase)
        scenario?.recreate()
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.enable_wallpaper)).assertIsDisplayed()
        Assert.assertEquals(SkyPhase.DAWN, SkyRepository(context).manualPhase)
    }

    @Test fun manifestRequestsGpsAndBackgroundWithApproximateFallback() {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        Assert.assertTrue(permissions.contains("android.permission.ACCESS_COARSE_LOCATION"))
        Assert.assertTrue(permissions.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
        Assert.assertTrue(permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
    }

    @Test fun settingsSwitchWorksFromTheWholeRowAndBackReturnsToHome() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsOn().performClick()
        Assert.assertFalse(SkyRepository(context).automatic)
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsOff()
        scenario?.recreate()
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsOff().performClick()
        Assert.assertTrue(SkyRepository(context).automatic)
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).assertIsDisplayed()
    }

    @Test fun predictiveBackPreviewsHomeCanCancelAndThenCommitWithoutLosingPreview() {
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        compose.onNodeWithText(context.getString(R.string.event_sunset)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
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
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).assertExists()
        scenario?.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.follow_sun)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.fix_preview, context.getString(R.string.phase_dusk))).assertIsDisplayed()
        startAndProgress()
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).assertIsDisplayed()
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
        compose.onNodeWithText(context.getString(R.string.color_stop, 1)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("123456")
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        scenario?.recreate()
        val store = SkyRepository(context).wallpapers
        Assert.assertEquals(GradientType.RADIAL, store.type)
        Assert.assertEquals(0xFF123456.toInt(), store.gradient(SkyPhase.DAWN).stops.first().color)
        compose.onNodeWithText(context.getString(R.string.picture_mode)).performClick()
        Assert.assertEquals(WallpaperMode.IMAGE, SkyRepository(context).wallpapers.mode)
    }

    @Test fun photoGradientEditorDragsPointsAndStopsAndKeepsUnsavedDraftAcrossRecreation() {
        openGradientEditor(GradientType.LINEAR)
        val preview = compose.onNodeWithTag("gradient-preview").fetchSemanticsNode().boundsInRoot
        val metrics = context.resources.displayMetrics
        Assert.assertTrue(preview.width < metrics.widthPixels)
        Assert.assertTrue(preview.height < metrics.heightPixels)
        Assert.assertEquals(metrics.widthPixels.toFloat() / metrics.heightPixels, preview.width / preview.height, .002f)
        Assert.assertTrue(preview.bottom < compose.onNodeWithTag("gradient-tools").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithContentDescription(context.getString(R.string.gradient_start)).performTouchInput {
            swipe(center, center + Offset(140f, 200f))
        }
        compose.onNodeWithContentDescription(context.getString(R.string.color_stop, 2)).performTouchInput {
            swipe(center, center + Offset(300f, 0f))
        }
        val draggedStop = compose.onNodeWithContentDescription(context.getString(R.string.color_stop, 2))
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current
        Assert.assertTrue("Color stop did not drag: $draggedStop", draggedStop > .55f)
        scenario?.recreate()
        compose.onNodeWithText(context.getString(R.string.color_stop, 2)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        val saved = SkyRepository(context).wallpapers.gradient(SkyPhase.DAWN)
        Assert.assertTrue("Start point did not move horizontally", saved.start.x > .53f)
        Assert.assertTrue("Start point did not move vertically", saved.start.y > .02f)
        Assert.assertEquals("Color stop changed after recreation", draggedStop, saved.stops[1].position, .00001f)
        Assert.assertEquals(0f, saved.stops[0].position, 0f)
        Assert.assertEquals(1f, saved.stops.last().position, 0f)
    }

    @Test fun radialAndSweepCentersDragAndPreciseControlsAreAccessible() {
        for (type in listOf(GradientType.RADIAL, GradientType.SWEEP)) {
            if (type == GradientType.RADIAL) openGradientEditor(type) else {
                compose.onNodeWithText(context.getString(R.string.gradient_type)).performClick()
                compose.onNodeWithText(context.getString(type.titleRes)).performClick()
                compose.onNodeWithText(context.getString(R.string.phase_dawn)).performClick()
            }
            compose.onNodeWithContentDescription(context.getString(R.string.gradient_center)).performTouchInput {
                swipe(center, center + Offset(160f, -160f))
            }
            compose.onNodeWithContentDescription(context.getString(R.string.gradient_geometry)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.gradient_center) + ", " + context.getString(R.string.coordinate_x)).assertExists()
            compose.onNodeWithContentDescription(context.getString(R.string.gradient_center) + ", " + context.getString(R.string.coordinate_y)).assertExists()
            compose.onNodeWithText(context.getString(R.string.done)).performClick()
            compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
            val saved = SkyRepository(context).wallpapers.gradient(SkyPhase.DAWN)
            Assert.assertTrue(saved.center.x > .39f)
            Assert.assertTrue(saved.center.y < .68f)
        }
    }

    private fun openGradientEditor(type: GradientType) {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.wallpaper_content)).performClick()
        compose.onNodeWithText(context.getString(R.string.gradient_mode)).performClick()
        compose.onNodeWithText(context.getString(R.string.gradient_type)).performClick()
        compose.onNode(hasText(context.getString(type.titleRes)) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText(context.getString(R.string.phase_dawn)).performClick()
    }

    @Test fun gradientEditorKeepsItsOriginalTypeWhenTheGlobalTypeChanges() {
        openGradientEditor(GradientType.RADIAL)
        compose.onNodeWithText(context.getString(R.string.color_stop, 1)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("123456")
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        scenario?.onActivity { SkyRepository(it).wallpapers.setType(GradientType.SWEEP) }
        scenario?.recreate()
        compose.onNodeWithContentDescription(context.getString(R.string.gradient_center)).assertExists()
        compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        val store = SkyRepository(context).wallpapers
        Assert.assertEquals(GradientType.SWEEP, store.type)
        Assert.assertEquals(0xFF123456.toInt(), store.gradient(SkyPhase.DAWN, GradientType.RADIAL).stops.first().color)
        Assert.assertEquals(SkyGradient.default(SkyPhase.DAWN, GradientType.SWEEP), store.gradient(SkyPhase.DAWN, GradientType.SWEEP))
        Assert.assertEquals(SkyGradient.default(SkyPhase.DAWN, GradientType.LINEAR), store.gradient(SkyPhase.DAWN, GradientType.LINEAR))
    }

    @Test fun radialRadiusReachesFourHundredPercentInBothEditorsAndPersists() {
        openGradientEditor(GradientType.RADIAL)
        val radius = context.getString(R.string.gradient_radius)
        compose.onNodeWithContentDescription(radius).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4f) }
        scenario?.recreate()
        Assert.assertEquals(4f, compose.onNodeWithContentDescription(radius).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        compose.onNodeWithContentDescription(context.getString(R.string.gradient_geometry)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(radius).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.1f) }
        compose.onNodeWithContentDescription(radius)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4f) }
        compose.onNodeWithText(context.getString(R.string.percent_format, 400)).assertExists()
        compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        Assert.assertEquals(4f, SkyRepository(context).wallpapers.gradient(SkyPhase.DAWN, GradientType.RADIAL).radius, 0f)
    }

    @Test fun switchingGradientTypesRestoresTheirOwnColorsAfterRecreation() {
        val colors = mapOf(GradientType.LINEAR to "123456", GradientType.RADIAL to "345678", GradientType.SWEEP to "56789A")
        fun open(type: GradientType) {
            compose.onNodeWithText(context.getString(R.string.gradient_type)).performClick()
            compose.onNode(hasText(context.getString(type.titleRes)) and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText(context.getString(R.string.phase_dawn)).performClick()
        }
        colors.entries.forEachIndexed { index, (type, value) ->
            if (index == 0) openGradientEditor(type) else open(type)
            compose.onNodeWithText(context.getString(R.string.color_stop, 1)).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(value)
            compose.onNodeWithText(context.getString(R.string.done)).performClick()
            compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        }
        colors.entries.reversed().forEach { (type, value) ->
            open(type)
            compose.onNodeWithText(context.getString(R.string.color_stop, 1)).performClick()
            compose.onNode(hasSetTextAction()).assertTextContains(value)
            scenario?.recreate()
            compose.onNode(hasSetTextAction()).assertTextContains(value)
            compose.onNodeWithText(context.getString(R.string.done)).performClick()
            compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        }
    }

    @Test fun wallpaperConfigurationActionsRestoreWithNavigation() {
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNodeWithText(context.getString(R.string.wallpaper_content)).performClick()
        compose.onNodeWithText(context.getString(R.string.wallpaper_config_title)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.export_wallpaper_config)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_wallpaper_config)).assertIsDisplayed()
        scenario?.recreate()
        compose.onNodeWithText(context.getString(R.string.export_wallpaper_config)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_wallpaper_config)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.wallpaper_content)).assertIsDisplayed()
    }

    @Test fun rtlGradientGeometryKeepsPhysicalCoordinatesAndColorStopsDirection() {
        openGradientEditor(GradientType.LINEAR)
        val manager = context.getSystemService(android.app.LocaleManager::class.java)
        val locales = manager.applicationLocales
        try {
            manager.applicationLocales = android.os.LocaleList.forLanguageTags("ar")
            scenario?.recreate()
            val arabic = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag("ar"))
            })
            compose.onNodeWithContentDescription(arabic.getString(R.string.gradient_start)).performTouchInput {
                swipe(center, center + Offset(200f, 200f))
            }
            compose.onNodeWithContentDescription(arabic.getString(R.string.color_stop, 2)).performTouchInput {
                swipe(center, center + Offset(300f, 0f))
            }
            compose.onNodeWithText(arabic.getString(R.string.save_location)).performClick()
            val saved = SkyRepository(context).wallpapers.gradient(SkyPhase.DAWN)
            Assert.assertTrue("RTL drag right must increase physical X", saved.start.x > .55f)
            Assert.assertTrue("RTL drag right must increase stop position", saved.stops[1].position > .55f)
        } finally {
            manager.applicationLocales = locales
            scenario?.recreate()
        }
    }

    @Test fun colorAndGeometryToolsKeepThePhotoVisibleWithKeyboardAndRestoreDraft() {
        openGradientEditor(GradientType.LINEAR)
        compose.onNodeWithText(context.getString(R.string.color_stop, 1)).performClick()
        compose.onNode(hasSetTextAction()).performClick().performTextReplacement("123456")
        fun assertPhotoAboveTools() {
            val photo = compose.onNodeWithTag("gradient-preview").fetchSemanticsNode().boundsInRoot
            val tools = compose.onNodeWithTag("gradient-tools").fetchSemanticsNode().boundsInRoot
            Assert.assertTrue("Preview covered by tools", photo.bottom < tools.top)
            Assert.assertTrue("Preview collapsed", photo.height > 100)
            val metrics = context.resources.displayMetrics
            Assert.assertEquals(metrics.widthPixels.toFloat() / metrics.heightPixels, photo.width / photo.height, .004f)
            compose.onNodeWithContentDescription(context.getString(R.string.gradient_start)).assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(R.string.gradient_end)).assertIsDisplayed()
            compose.onAllNodes(isDialog()).assertCountEquals(0)
        }
        assertPhotoAboveTools()
        scenario?.recreate()
        compose.onNode(hasSetTextAction()).assertTextContains("123456")
        assertPhotoAboveTools()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.gradient_geometry)).performClick()
        assertPhotoAboveTools()
        compose.onNodeWithText(context.getString(R.string.done)).performClick()
        compose.onNodeWithText(context.getString(R.string.save_location)).performClick()
        Assert.assertEquals(0xFF123456.toInt(), SkyRepository(context).wallpapers.gradient(SkyPhase.DAWN).stops[0].color)
    }

    @Test fun openSourceLicensesAreAvailableOfflineAndRestoreNavigation() {
        val libraries = OpenSourceLicenses.libraries(context)
        Assert.assertEquals(libraries.size, libraries.map { it.id }.toSet().size)
        Assert.assertTrue(libraries.any { it.id == "androidx" })
        Assert.assertTrue(libraries.any { it.id == "material-symbols" })
        for (entry in libraries + OpenSourceLicenses.project) {
            val legalText = context.assets.open(entry.asset).bufferedReader().use { it.readText() }
            Assert.assertTrue("Missing license for ${entry.name}", legalText.contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION"))
            Assert.assertTrue("Missing attribution for ${entry.name}", legalText.contains("Copyright"))
        }
        compose.onNodeWithContentDescription(context.getString(R.string.settings)).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(context.getString(R.string.open_source_licenses)))
        compose.onNodeWithText(context.getString(R.string.open_source_licenses)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.source_code)).assertIsDisplayed()
        compose.onNodeWithText("Sky Wallpaper").performClick()
        compose.onNodeWithText("Copyright 2026 RiversLee9312", substring = true).assertIsDisplayed()
        scenario?.recreate()
        compose.onNodeWithText("Copyright 2026 RiversLee9312", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText("AndroidX").performScrollTo().performClick()
        compose.onNodeWithText("Copyright The Android Open Source Project").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.project_license)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithText(context.getString(R.string.open_source_licenses)).assertIsDisplayed()
    }

    @Test fun changingPhoneTimezoneKeepsUtcSunriseUnchanged() {
        compose.onNodeWithText(context.getString(R.string.view_wallpaper)).performClick()
        val originalZone = java.util.TimeZone.getDefault().id
        val now = Instant.now()
        val sunrise = SolarClock.events(now, GeoPoint(39.90, 116.40)).first { it.type == SolarEventType.SUNRISE }
        val utc = java.time.ZoneOffset.UTC
        val pattern = "MM-dd HH:mm"
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
