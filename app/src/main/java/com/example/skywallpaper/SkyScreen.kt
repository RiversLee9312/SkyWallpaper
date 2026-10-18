package com.example.skywallpaper

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val utcTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)
private val utcDateTime = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)
// Keep invariant coordinates and UTC values together inside a translated RTL sentence.
private fun ltrValue(value: String, direction: LayoutDirection): String =
    BidiFormatter.getInstance(direction == LayoutDirection.Rtl).unicodeWrap(value, TextDirectionHeuristics.LTR)
private fun coordinate(point: GeoPoint) = String.format(Locale.ROOT, "%.2f°%s · %.2f°%s",
    kotlin.math.abs(point.latitude), if (point.latitude >= 0) "N" else "S",
    kotlin.math.abs(point.longitude), if (point.longitude >= 0) "E" else "W")

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun SkyScreen(repository: SkyRepository, revision: Int, locationStatus: String, stopLocation: () -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val direction = LocalLayoutDirection.current
    var now by remember { mutableStateOf(Instant.now()) }
    val store = repository.wallpapers
    LaunchedEffect(Unit) { while (true) {
        now = Instant.now()
        delay(if (store.mode == WallpaperMode.GRADIENT && repository.automatic) 1_000 else 10_000)
    } }
    val current = remember(now, revision) { repository.phase(now) }
    val point = remember(revision) { repository.point }
    val automatic = remember(revision) { repository.automatic }
    val liveGradient = remember(now, revision) {
        if (store.mode == WallpaperMode.GRADIENT) store.frame(now, point, automatic, current) else null
    }
    var preview by rememberSaveable { mutableStateOf<SkyPhase?>(null) }
    val selected = preview ?: current
    val selectedTitle = stringResource(selected.titleRes)
    val currentTitle = stringResource(current.titleRes)
    val navigation = rememberNavController()
    var manualLocationDialog by rememberSaveable { mutableStateOf(false) }
    var backgroundDialog by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            refresh(); if (!context.hasBackground()) backgroundDialog = true
        } else notice = resources.getString(R.string.permission_denied_notice)
    }
    fun requestLocation() { if (context.hasFine()) refresh() else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val wallpaper = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun activate() {
        try {
            wallpaper.launch(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, SkyWallpaperService::class.java)))
        } catch (_: android.content.ActivityNotFoundException) { notice = resources.getString(R.string.wallpaper_unavailable) }
    }
    val next = remember(now.epochSecond / 60, point, automatic) {
        point?.takeIf { automatic }?.let { SolarClock.nextTransition(now, it) }
    }
    val events = remember(now.epochSecond / 60, point) { point?.let { SolarClock.events(now, it) }.orEmpty() }
    val active = context.getSystemService(WallpaperManager::class.java).wallpaperInfo?.packageName == context.packageName
    val colors = MaterialTheme.colorScheme
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val homeScroll = rememberScrollState()
    val forwardDirection = if (direction == LayoutDirection.Ltr) 1 else -1
    // NavHost owns the back stack and seekable transitions, including cancelled predictive gestures.
    // Keep preview/dialog state above the host so both destinations share it during transitions.
    NavHost(
        navController = navigation,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().background(colors.background),
        enterTransition = { slideInHorizontally(spatialSpec) { it / 8 * forwardDirection } + fadeIn(effectsSpec) },
        exitTransition = { scaleOut(effectsSpec, targetScale = .96f) + fadeOut(effectsSpec) },
        popEnterTransition = { scaleIn(effectsSpec, initialScale = .96f) + fadeIn(effectsSpec) },
        popExitTransition = { scaleOut(effectsSpec, targetScale = .92f) + fadeOut(effectsSpec) },
    ) {
        composable("home") {
            Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(homeScroll)
                    .padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.headline), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(painterResource(if (automatic) R.drawable.ic_symbol_routine else R.drawable.ic_symbol_wallpaper), null,
                                    Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                                Text(if (automatic && point != null) stringResource(R.string.status_auto) else if (automatic) stringResource(R.string.status_waiting) else stringResource(R.string.status_fixed),
                                    style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            }
                        }
                        FilledTonalIconButton(onClick = { navigation.navigate("settings") { launchSingleTop = true } }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_symbol_settings), stringResource(R.string.settings))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.extraLarge)) {
                        Crossfade(targetState = selected, animationSpec = effectsSpec, label = "sky preview") { phase ->
                            WallpaperArtwork(store, phase, revision, Modifier.fillMaxSize(),
                                stringResource(R.string.image_description, stringResource(phase.titleRes)),
                                liveFrame = liveGradient.takeIf { preview == null && phase == current })
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xA6001026)))))
                        Surface(Modifier.align(Alignment.TopStart).padding(16.dp), color = Color(0xFF172431).copy(alpha = .72f), shape = MaterialTheme.shapes.extraLarge) {
                            Text(if (selected == current) stringResource(R.string.current_sky) else stringResource(R.string.preview_badge),
                                Modifier.padding(12.dp, 7.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        if (selected != current) {
                            FilledTonalIconButton(onClick = { preview = null }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF172431).copy(alpha = .72f), contentColor = Color.White)) {
                                Icon(painterResource(R.drawable.ic_symbol_history), stringResource(R.string.reset_preview))
                            }
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                            Text(selectedTitle, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkyGallery(store, revision, selected, onSelect = { preview = it })
                        if (selected != current) {
                            Text(stringResource(R.string.preview_status, selectedTitle, currentTitle), style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        Button(onClick = { activate() }, modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                            Icon(painterResource(R.drawable.ic_symbol_wallpaper), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (active) stringResource(R.string.view_wallpaper) else stringResource(R.string.enable_wallpaper), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                        }
                    }
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.today), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.utc_label), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                            }
                            if (point == null) {
                                Text(stringResource(R.string.connect_sky), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.location_intro), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                FilledTonalButton(onClick = { requestLocation() }, shapes = ButtonDefaults.shapes()) {
                                    Icon(painterResource(R.drawable.ic_symbol_my_location), null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.get_gps))
                                }
                                TextButton(onClick = { manualLocationDialog = true }) { Text(stringResource(R.string.manual_coordinates)) }
                            } else {
                                SunArc(now, point)
                                Text(if (!automatic) stringResource(R.string.fixed_using, currentTitle) else next?.let {
                                    resources.getString(R.string.next_sky, resources.getString(it.phase.titleRes), ltrValue("${utcDateTime.format(it.instant)} UTC", direction))
                                } ?: stringResource(R.string.no_transition), style = MaterialTheme.typography.titleMedium)
                                events.filter { it.type == SolarEventType.SUNRISE || it.type == SolarEventType.SUNSET }.forEach { event ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(painterResource(if (event.type == SolarEventType.SUNRISE) R.drawable.ic_symbol_wb_twilight else R.drawable.ic_symbol_dark_mode), null,
                                                Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                                            Text(stringResource(event.type.titleRes), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                        }
                                        val sameUtcDate = event.instant.atOffset(ZoneOffset.UTC).toLocalDate() == now.atOffset(ZoneOffset.UTC).toLocalDate()
                                        Text(ltrValue((if (sameUtcDate) utcTime else utcDateTime).format(event.instant), direction),
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                }
                                if (events.none { it.type == SolarEventType.SUNRISE || it.type == SolarEventType.SUNSET })
                                    Text(stringResource(R.string.polar_notice), style = MaterialTheme.typography.bodySmall)
                                Text(ltrValue(coordinate(point), direction), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                                val age = repository.updatedAt?.let { now.epochSecond - it.epochSecond } ?: 0
                                if (!repository.manualLocation && age > 86400) Text(stringResource(R.string.stale_location), color = colors.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (locationStatus.isNotBlank()) Text(locationStatus, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        composable("settings") {
            SkySettingsScreen(
                automatic = automatic,
                selected = selected,
                locationSummary = point?.let { resources.getString(if (repository.manualLocation) R.string.manual_location_format else R.string.device_location_format, ltrValue(coordinate(it), direction)) }
                    ?: stringResource(R.string.no_location),
                updatedSummary = repository.updatedAt?.let { resources.getString(R.string.updated_format, ltrValue("${utcDateTime.format(it)} UTC", direction)) },
                locationStatus = locationStatus,
                permissionSummary = stringResource(R.string.permission_format,
                    stringResource(when { context.hasBackground() -> R.string.permission_background; context.hasCoarse() -> R.string.permission_foreground; else -> R.string.permission_none }),
                    if (context.hasFine()) stringResource(R.string.permission_precise) else if (context.hasCoarse()) stringResource(R.string.permission_approximate) else ""),
                backgroundAllowed = context.hasBackground(),
                onBack = { navigation.popBackStack() },
                onAutomaticChanged = { value ->
                    if (!value) stopLocation()
                    repository.setAutomatic(value)
                    scheduleLocationRefresh(context)
                },
                onFixPreview = {
                    stopLocation(); repository.setManualPhase(selected); repository.setAutomatic(false)
                    scheduleLocationRefresh(context); navigation.popBackStack()
                },
                onRefresh = { requestLocation() },
                onManualLocation = { manualLocationDialog = true },
                onBackgroundPermission = { if (context.hasFine()) backgroundDialog = true else requestLocation() },
                onSystemPermissions = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                onLanguage = { context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                wallpaperSummary = stringResource(if (store.mode == WallpaperMode.IMAGE) R.string.picture_mode else R.string.gradient_mode),
                onWallpapers = { navigation.navigate("wallpapers") },
                wallpaperThumbnail = { modifier -> WallpaperArtwork(store, selected, revision, modifier) },
            )
        }
        composable("wallpapers") {
            WallpaperEditorScreen(store, revision, onBack = { navigation.popBackStack() }, onGradient = { phase -> navigation.navigate("gradient/${phase.name}") })
        }
        composable("gradient/{phase}") { entry ->
            val phase = SkyPhase.entries.find { it.name == entry.arguments?.getString("phase") } ?: SkyPhase.DAY
            GradientEditorScreen(phase, store.type, store.gradient(phase), onBack = { navigation.popBackStack() }, onSave = {
                store.saveGradient(phase, it); navigation.popBackStack()
            })
        }
    }
    if (backgroundDialog) AlertDialog(onDismissRequest = { backgroundDialog = false }, title = { Text(stringResource(R.string.background_title)) },
        text = { Text(stringResource(R.string.background_explanation)) },
        confirmButton = { TextButton(onClick = { backgroundDialog = false; backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text(stringResource(R.string.grant_access)) } },
        dismissButton = { TextButton(onClick = { backgroundDialog = false }) { Text(stringResource(R.string.skip)) } })
    if (manualLocationDialog) LocationDialog(point, onDismiss = { manualLocationDialog = false }) {
        stopLocation(); repository.savePoint(it, manual = true); scheduleLocationRefresh(context); manualLocationDialog = false
    }
    notice?.let { message -> AlertDialog(onDismissRequest = { notice = null }, title = { Text(stringResource(R.string.notice)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { notice = null }) { Text(stringResource(R.string.ok)) } }) }
}

private fun Context.hasCoarse() = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
private fun Context.hasFine() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
private fun Context.hasBackground() = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun LocationDialog(point: GeoPoint?, onDismiss: () -> Unit, onSave: (GeoPoint) -> Unit) {
    var latitude by rememberSaveable { mutableStateOf(point?.latitude?.toString() ?: "") }
    var longitude by rememberSaveable { mutableStateOf(point?.longitude?.toString() ?: "") }
    val lat = latitude.toDoubleOrNull()
    val lon = longitude.toDoubleOrNull()
    val valid = lat != null && lon != null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.manual_location_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.manual_location_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(latitude, { latitude = it }, label = { Text(stringResource(R.string.latitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            OutlinedTextField(longitude, { longitude = it }, label = { Text(stringResource(R.string.longitude)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(GeoPoint(lat!!, lon!!)) }) { Text(stringResource(R.string.save_location)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SunArc(now: Instant, point: GeoPoint) {
    val colors = MaterialTheme.colorScheme
    val start = SolarClock.dayStart(now, point)
    val sun = SolarClock.position(now, point)
    val points = remember(start, point) { (0..96).map { SolarClock.position(start.plusSeconds(it * 900L), point).elevation } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(82.dp)) {
            val horizon = size.height * .65f
            fun y(elevation: Double) = (horizon - elevation / 90 * size.height * .55).toFloat()
            drawLine(colors.outlineVariant, androidx.compose.ui.geometry.Offset(0f, horizon), androidx.compose.ui.geometry.Offset(size.width, horizon), strokeWidth = 1.dp.toPx())
            val path = Path()
            points.forEachIndexed { index, elevation -> if (index == 0) path.moveTo(0f, y(elevation)) else path.lineTo(index / 96f * size.width, y(elevation)) }
            drawPath(path, colors.primary.copy(alpha = .55f), style = Stroke(3.dp.toPx()))
            val x = ((now.epochSecond - start.epochSecond) / 86400f).coerceIn(0f, 1f) * size.width
            drawCircle(colors.tertiaryContainer, 12.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y(sun.elevation)))
            drawCircle(colors.primary, 6.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y(sun.elevation)))
        }
        Text(stringResource(R.string.solar_elevation, ltrValue(String.format(Locale.ROOT, "%+.1f", sun.elevation), LocalLayoutDirection.current), stringResource(if (sun.rising) R.string.sun_rising else R.string.sun_falling)),
            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}
