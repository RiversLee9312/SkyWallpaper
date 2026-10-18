package com.example.skywallpaper

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val previewTime = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)

/** A simulation of the saved palettes and images; changing time never writes wallpaper preferences. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun WallpaperPreviewScreen(store: WallpaperStore, revision: Int, point: GeoPoint?, now: Instant, fallback: SkyPhase,
                           locationStatus: String, onBack: () -> Unit, onPhase: (SkyPhase?) -> Unit,
                           onLocation: () -> Unit, onManualLocation: () -> Unit, initialPhase: SkyPhase? = null) {
    val initialAnchor = remember {
        point?.takeIf { initialPhase != null }?.let { location -> SolarGradientClock.anchors(now, location).firstOrNull {
            it.phase == initialPhase && SolarClock.dayStart(it.instant, location) == SolarClock.dayStart(now, location)
        } }
    }
    var pickedMillis by rememberSaveable { mutableStateOf(initialAnchor?.instant?.toEpochMilli()) }
    var pickedPhase by rememberSaveable { mutableStateOf(initialPhase.takeIf { initialAnchor == null }) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val time = pickedMillis?.let(Instant::ofEpochMilli) ?: now
    val phase = pickedPhase ?: point?.let { SolarClock.phase(time, it) } ?: fallback
    val frame = remember(time, point, phase, pickedPhase, revision) {
        if (store.mode == WallpaperMode.GRADIENT) store.frame(time, point, automatic = pickedPhase == null, phase) else null
    }
    LaunchedEffect(phase, pickedMillis == null, pickedPhase) {
        onPhase(if (pickedMillis == null && pickedPhase == null) null else phase)
    }
    BackHandler(fullscreen) { fullscreen = false }
    PreviewSystemBars(fullscreen)
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val safe = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("wallpaper-preview-screen")) {
        if (frame != null) GradientArtwork(store.type, frame, Modifier.matchParentSize(),
            stringResource(R.string.image_description, stringResource(phase.titleRes)))
        else Crossfade(phase, animationSpec = effects, label = "preview wallpaper") { imagePhase ->
            WallpaperArtwork(store, imagePhase, revision, Modifier.fillMaxSize(),
                stringResource(R.string.image_description, stringResource(imagePhase.titleRes)))
        }
        // Only the system bars need a scrim. The wallpaper stays unobstructed in immersive mode.
        if (!fullscreen) {
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Black.copy(alpha = .35f)))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(Color.Black.copy(alpha = .45f)))
        }
        AnimatedVisibility(!fullscreen, Modifier.align(Alignment.TopStart).windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(12.dp),
            enter = fadeIn(effects), exit = fadeOut(effects)) {
            FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
            }
        }
        FilledTonalIconButton(onClick = { fullscreen = !fullscreen }, shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(safe.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(12.dp).size(48.dp)) {
            Icon(painterResource(if (fullscreen) R.drawable.ic_symbol_fullscreen_exit else R.drawable.ic_symbol_fullscreen),
                stringResource(if (fullscreen) R.string.exit_fullscreen else R.string.enter_fullscreen))
        }
        val landscape = maxWidth > maxHeight
        val cardHeight = maxHeight * if (landscape) .68f else .65f
        AnimatedVisibility(!fullscreen,
            Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                .windowInsetsPadding(safe.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(12.dp).widthIn(max = if (landscape) 400.dp else 480.dp),
            enter = fadeIn(effects), exit = fadeOut(effects)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().heightIn(max = cardHeight).testTag("preview-time-card")) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(if (landscape) 12.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (landscape) 8.dp else 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.utc_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (point != null) {
                        val start = remember(time.epochSecond / 60, point) { SolarClock.dayStart(time, point) }
                        val events = remember(start, point) { SolarClock.events(start.plusSeconds(43200), point) }
                        val direction = LocalLayoutDirection.current
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ltrValue(previewTime.format(time), direction), style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.testTag("preview-selected-time"))
                                Text(stringResource(phase.titleRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (pickedMillis != null || pickedPhase != null) FilledTonalIconButton(onClick = { pickedMillis = null; pickedPhase = null }, shapes = IconButtonDefaults.shapes()) {
                                Icon(painterResource(R.drawable.ic_symbol_history), stringResource(R.string.reset_preview))
                            }
                        }
                        val description = stringResource(R.string.preview_time)
                        // Time and solar geometry run from left to right in every language.
                        SunArc(time, start, point, compact = landscape)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Slider(value = ((time.toEpochMilli() - start.toEpochMilli()) / 1000f).coerceIn(0f, 86399f),
                                onValueChange = { pickedPhase = null; pickedMillis = start.plusSeconds(it.roundToLong()).toEpochMilli() }, valueRange = 0f..86399f,
                                modifier = Modifier.testTag("preview-time-slider").semantics {
                                    contentDescription = description
                                    stateDescription = previewTime.format(time)
                                })
                        }
                        events.filter { it.type == SolarEventType.SUNRISE || it.type == SolarEventType.SUNSET }.forEach { event ->
                            Surface(onClick = { pickedPhase = null; pickedMillis = event.instant.toEpochMilli() }, shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(painterResource(if (event.type == SolarEventType.SUNRISE) R.drawable.ic_symbol_wb_twilight else R.drawable.ic_symbol_dark_mode),
                                        null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(event.type.titleRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(ltrValue(previewTime.format(event.instant), direction), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        if (events.none { it.type == SolarEventType.SUNRISE || it.type == SolarEventType.SUNSET })
                            Text(stringResource(R.string.polar_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SkyGallery(store, revision, phase, onSelect = { pickedPhase = it })
                        Text(stringResource(R.string.connect_sky), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.location_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = onLocation, shapes = ButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_symbol_my_location), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.get_gps))
                        }
                        TextButton(onClick = onManualLocation) { Text(stringResource(R.string.manual_coordinates)) }
                    }
                    if (locationStatus.isNotBlank()) Text(locationStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PreviewSystemBars(fullscreen: Boolean) {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    val dark by rememberUpdatedState(isSystemInDarkTheme())
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val behavior = controller?.systemBarsBehavior
        onDispose {
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                behavior?.let { controller.systemBarsBehavior = it }
            }
        }
    }
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun SunArc(time: Instant, start: Instant, point: GeoPoint, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    val sun = SolarClock.position(time, point)
    val points = remember(start, point) { (0..96).map { SolarClock.position(start.plusSeconds(it * 900L), point).elevation } }
    Canvas(Modifier.fillMaxWidth().height(if (compact) 40.dp else 72.dp)) {
        val horizon = size.height / 2
        val margin = 12.dp.toPx()
        fun y(elevation: Double) = (margin + (90 - elevation) / 180 * (size.height - 2 * margin)).toFloat()
        drawLine(colors.outlineVariant, Offset(0f, horizon), Offset(size.width, horizon), strokeWidth = 1.dp.toPx())
        val path = Path()
        points.forEachIndexed { index, elevation ->
            if (index == 0) path.moveTo(0f, y(elevation)) else path.lineTo(index / 96f * size.width, y(elevation))
        }
        drawPath(path, colors.primary.copy(alpha = .55f), style = Stroke(3.dp.toPx()))
        val x = ((time.epochSecond - start.epochSecond) / 86400f).coerceIn(0f, 1f) * size.width
        drawCircle(colors.tertiaryContainer, 10.dp.toPx(), Offset(x, y(sun.elevation)))
        drawCircle(colors.primary, 5.dp.toPx(), Offset(x, y(sun.elevation)))
    }
    Text(stringResource(R.string.solar_elevation, ltrValue(String.format(Locale.ROOT, "%+.1f", sun.elevation), LocalLayoutDirection.current),
        stringResource(if (sun.rising) R.string.sun_rising else R.string.sun_falling)),
        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
}
