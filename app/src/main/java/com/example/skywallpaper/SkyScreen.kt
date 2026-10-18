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
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val utcDateTime = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC)
// Keep invariant coordinates and UTC values together inside a translated RTL sentence.
internal fun ltrValue(value: String, direction: LayoutDirection): String =
    BidiFormatter.getInstance(direction == LayoutDirection.Rtl).unicodeWrap(value, TextDirectionHeuristics.LTR)
internal fun coordinate(point: GeoPoint) = String.format(Locale.ROOT, "%.2f°%s · %.2f°%s",
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
                            Text(if (preview == null) stringResource(R.string.current_sky) else stringResource(R.string.preview_badge),
                                Modifier.padding(12.dp, 7.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        if (preview != null) {
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
                        if (preview != null) {
                            Text(stringResource(R.string.preview_status, selectedTitle, currentTitle), style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        Button(onClick = { navigation.navigate("preview") { launchSingleTop = true } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                            Icon(painterResource(R.drawable.ic_symbol_visibility), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.view_wallpaper), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                        }
                        FilledTonalButton(onClick = ::activate,
                            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                            shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                            Icon(painterResource(R.drawable.ic_symbol_wallpaper), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.enable_wallpaper), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                        }
                    }
                }
            }
        }
        composable("preview") {
            WallpaperPreviewScreen(store, revision, point, now, repository.manualPhase, locationStatus,
                onBack = { navigation.popBackStack() }, onPhase = { preview = it },
                onLocation = ::requestLocation, onManualLocation = { manualLocationDialog = true }, initialPhase = preview)
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
                onLicenses = { navigation.navigate("licenses") },
                wallpaperSummary = stringResource(if (store.mode == WallpaperMode.IMAGE) R.string.picture_mode else R.string.gradient_mode),
                onWallpapers = { navigation.navigate("wallpapers") },
                wallpaperThumbnail = { modifier -> WallpaperArtwork(store, selected, revision, modifier) },
            )
        }
        composable("wallpapers") {
            WallpaperEditorScreen(store, revision, onBack = { navigation.popBackStack() },
                onGradient = { phase, type -> navigation.navigate("gradient/${type.name}/${phase.name}") },
                onConfiguration = { navigation.navigate("wallpaper-config") })
        }
        composable("wallpaper-config") { entry ->
            val transfer = remember(entry) {
                ViewModelProvider(entry, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        WallpaperTransferModel(context.applicationContext, store, entry.savedStateHandle) as T
                })[WallpaperTransferModel::class.java]
            }
            WallpaperConfigurationScreen(transfer, onBack = { navigation.popBackStack() }, onImported = {
                stopLocation(); preview = null; scheduleLocationRefresh(context)
            })
        }
        composable("licenses") {
            OpenSourceScreen(onBack = { navigation.popBackStack() }, onLicense = { navigation.navigate("license/$it") },
                onSource = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/RiversLee9312/SkyWallpaper".toUri())) })
        }
        composable("license/{id}") { entry ->
            LicenseTextScreen(entry.arguments?.getString("id") ?: "project", onBack = { navigation.popBackStack() })
        }
        composable("gradient/{type}/{phase}") { entry ->
            val phase = SkyPhase.entries.find { it.name == entry.arguments?.getString("phase") } ?: SkyPhase.DAY
            val type = GradientType.entries.find { it.name == entry.arguments?.getString("type") } ?: GradientType.LINEAR
            GradientEditorScreen(phase, type, store.gradient(phase, type), onBack = { navigation.popBackStack() }, onSave = {
                store.saveGradient(phase, it, type); navigation.popBackStack()
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
