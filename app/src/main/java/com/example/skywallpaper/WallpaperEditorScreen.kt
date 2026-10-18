package com.example.skywallpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

val GradientType.titleRes: Int get() = when (this) {
    GradientType.LINEAR -> R.string.gradient_linear
    GradientType.RADIAL -> R.string.gradient_radial
    GradientType.SWEEP -> R.string.gradient_sweep
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WallpaperEditorScreen(store: WallpaperStore, revision: Int, onBack: () -> Unit, onGradient: (SkyPhase) -> Unit) {
    val importError = stringResource(R.string.image_import_failed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme
    val mode = remember(revision) { store.mode }
    var imagePhase by rememberSaveable { mutableStateOf(SkyPhase.DAY) }
    var sheet by rememberSaveable { mutableStateOf<SkyPhase?>(null) }
    var importing by remember { mutableStateOf<SkyPhase?>(null) }
    var typeDialog by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val phase = imagePhase
            importing = phase
            scope.launch {
                try { store.importImage(phase, uri) }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { snackbar.showSnackbar(importError) }
                finally { importing = null }
            }
        }
    }
    Scaffold(
        topBar = { WallpaperTopBar(stringResource(R.string.wallpaper_content), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperMode.entries.forEach { item ->
                    ToggleButton(checked = mode == item, onCheckedChange = { store.setMode(item) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
                        Icon(painterResource(if (item == WallpaperMode.IMAGE) R.drawable.ic_symbol_photo_library else R.drawable.ic_symbol_gradient), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (item == WallpaperMode.IMAGE) R.string.picture_mode else R.string.gradient_mode))
                    }
                }
            }
            if (mode == WallpaperMode.GRADIENT) {
                SettingsRow(title = stringResource(R.string.gradient_type), summary = stringResource(store.type.titleRes),
                    icon = R.drawable.ic_symbol_tune, position = 0, count = 1, color = colors.surfaceContainerLow, onClick = { typeDialog = true })
                Text(stringResource(R.string.gradient_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SkyPhase.entries.forEachIndexed { index, phase ->
                    SegmentedListItem(
                        onClick = { if (mode == WallpaperMode.GRADIENT) onGradient(phase) else sheet = phase },
                        enabled = importing == null,
                        shapes = ListItemDefaults.segmentedShapes(index, SkyPhase.entries.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceContainerLow),
                        contentPadding = PaddingValues(16.dp),
                        leadingContent = {
                            WallpaperArtwork(store, phase, revision, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium))
                        },
                        supportingContent = {
                            Text(stringResource(if (importing == phase) R.string.image_importing else if (mode == WallpaperMode.GRADIENT) R.string.edit_gradient
                                else if (store.imageFile(phase) != null) R.string.custom_image else R.string.builtin_image))
                        },
                        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null) },
                    ) { Text(stringResource(phase.titleRes), fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
    sheet?.let { phase ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Text(stringResource(phase.titleRes), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            ListItem(headlineContent = { Text(stringResource(R.string.choose_image)) }, leadingContent = { Icon(painterResource(R.drawable.ic_symbol_photo_library), null) },
                modifier = Modifier.clickable {
                    sheet = null; imagePhase = phase
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            ListItem(headlineContent = { Text(stringResource(R.string.use_builtin)) }, leadingContent = { Icon(painterResource(R.drawable.ic_symbol_wallpaper), null) },
                modifier = Modifier.clickable { store.useBuiltIn(phase); sheet = null })
            Spacer(Modifier.height(24.dp))
        }
    }
    if (typeDialog) AlertDialog(onDismissRequest = { typeDialog = false },
        title = { Text(stringResource(R.string.gradient_type)) },
        text = {
            Column {
                Text(stringResource(R.string.gradient_shared_type), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                GradientType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { store.setType(type); typeDialog = false }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = store.type == type, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(type.titleRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { typeDialog = false }) { Text(stringResource(R.string.cancel)) } })
}

private val gradientSaver = Saver<SkyGradient, String>(save = { GradientCodec.encode(it) }, restore = { GradientCodec.decode(it) })

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GradientEditorScreen(phase: SkyPhase, type: GradientType, initial: SkyGradient, onBack: () -> Unit, onSave: (SkyGradient) -> Unit) {
    var draft by rememberSaveable(phase, stateSaver = gradientSaver) { mutableStateOf(initial) }
    var colorIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val valid = type != GradientType.LINEAR || abs(draft.start.x - draft.end.x) + abs(draft.start.y - draft.end.y) >= .01f
    val colors = MaterialTheme.colorScheme
    Scaffold(topBar = {
        WallpaperTopBar(stringResource(phase.titleRes), onBack) {
            IconButton(onClick = { reset = true }) { Icon(painterResource(R.drawable.ic_symbol_history), stringResource(R.string.reset_gradient)) }
            TextButton(onClick = { onSave(draft) }, enabled = valid) { Text(stringResource(R.string.save_location)) }
        }
    }, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            GradientArtwork(type, GradientFrame.from(draft), Modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.extraLarge),
                stringResource(R.string.image_description, stringResource(phase.titleRes)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gradient_colors), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = draft.copy(stops = addStop(draft.stops)) }, enabled = draft.stops.size < 6) {
                    Icon(painterResource(R.drawable.ic_symbol_add), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_color))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.stops.forEachIndexed { index, stop ->
                    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { colorIndex = index }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(stop.color)))
                                    Column {
                                        Text(stringResource(R.string.color_stop, index + 1), style = MaterialTheme.typography.labelLarge)
                                        Text("#${hex(stop.color)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { draft = draft.copy(stops = draft.stops.filterIndexed { i, _ -> i != index }) }, enabled = draft.stops.size > 2) {
                                    Icon(painterResource(R.drawable.ic_symbol_delete), stringResource(R.string.remove_color))
                                }
                            }
                            ParameterSlider(stringResource(R.string.stop_position), stop.position, onChange = { value ->
                                val min = if (index == 0) 0f else draft.stops[index - 1].position + .01f
                                val max = if (index == draft.stops.lastIndex) 1f else draft.stops[index + 1].position - .01f
                                draft = draft.copy(stops = draft.stops.mapIndexed { i, item -> if (i == index) item.copy(position = value.coerceIn(min, max)) else item })
                            }, description = stringResource(R.string.color_stop, index + 1) + ", " + stringResource(R.string.stop_position))
                        }
                    }
                }
            }
            Text(stringResource(R.string.geometry_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (type == GradientType.LINEAR) {
                PointControls(stringResource(R.string.gradient_start), draft.start) { draft = draft.copy(start = it) }
                PointControls(stringResource(R.string.gradient_end), draft.end) { draft = draft.copy(end = it) }
                if (!valid) Text(stringResource(R.string.gradient_invalid_points), color = colors.error, style = MaterialTheme.typography.bodySmall)
            } else {
                PointControls(stringResource(R.string.gradient_center), draft.center) { draft = draft.copy(center = it) }
                if (type == GradientType.RADIAL) EditorSection(stringResource(R.string.gradient_radius)) {
                    Text(stringResource(R.string.gradient_radius_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    ParameterSlider(stringResource(R.string.gradient_radius), draft.radius, { draft = draft.copy(radius = it) }, range = .1f..2f)
                }
            }
        }
    }
    colorIndex?.let { index ->
        ColorDialog(draft.stops[index].color, stringResource(R.string.color_stop, index + 1), onDismiss = { colorIndex = null }) { color ->
            draft = draft.copy(stops = draft.stops.mapIndexed { i, stop -> if (i == index) stop.copy(color = color) else stop })
            colorIndex = null
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(stringResource(R.string.reset_gradient_title)) },
        text = { Text(stringResource(R.string.reset_gradient_body, stringResource(phase.titleRes))) },
        confirmButton = { TextButton(onClick = { draft = SkyGradient.default(phase, type); reset = false }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WallpaperTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall) },
        navigationIcon = {
            FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
            }
        }, actions = actions, expandedHeight = 80.dp)
}

@Composable
private fun PointControls(title: String, point: GradientPoint, onChange: (GradientPoint) -> Unit) {
    EditorSection(title) {
        ParameterSlider(stringResource(R.string.coordinate_x), point.x, { onChange(point.copy(x = it)) }, description = title + ", " + stringResource(R.string.coordinate_x))
        ParameterSlider(stringResource(R.string.coordinate_y), point.y, { onChange(point.copy(y = it)) }, description = title + ", " + stringResource(R.string.coordinate_y))
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun ParameterSlider(label: String, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = 0f..1f,
                            description: String = label, valueText: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText ?: stringResource(R.string.percent_format, (value * 100).roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = description })
    }
}

private fun hex(color: Int) = String.format(Locale.ROOT, "%06X", color and 0xFFFFFF)

@Composable
private fun ColorDialog(initial: Int, title: String, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableStateOf(hex(initial)) }
    val initialHsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(initialHsv[2]) }
    val parsed = value.takeIf { it.matches(Regex("[0-9A-F]{6}")) }?.toLongOrNull(16)?.toInt()?.or(0xFF shl 24)
    fun change(index: Int, number: Float) {
        val hsv = floatArrayOf(hue, saturation, brightness).apply { this[index] = number }
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
        value = hex(android.graphics.Color.HSVToColor(hsv))
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, icon = {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(parsed ?: initial)))
    }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(value, { text ->
                value = text.uppercase(Locale.ROOT).take(6)
                value.takeIf { it.matches(Regex("[0-9A-F]{6}")) }?.toLongOrNull(16)?.let {
                    val hsv = FloatArray(3).also { values -> android.graphics.Color.colorToHSV(it.toInt() or (0xFF shl 24), values) }
                    hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                }
            }, label = { Text(stringResource(R.string.color_value)) },
                prefix = { Text("#") }, singleLine = true, isError = parsed == null,
                supportingText = if (parsed == null) { { Text(stringResource(R.string.gradient_invalid_color)) } } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii))
            Spacer(Modifier.height(16.dp))
            ParameterSlider(stringResource(R.string.color_hue), hue, { change(0, it) }, range = 0f..360f, valueText = "${hue.roundToInt()}°")
            ParameterSlider(stringResource(R.string.color_saturation), saturation, { change(1, it) })
            ParameterSlider(stringResource(R.string.color_brightness), brightness, { change(2, it) })
        }
    }, confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text(stringResource(R.string.save_location)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun addStop(stops: List<GradientStop>): List<GradientStop> {
    val positions = listOf(0f) + stops.map { it.position } + 1f
    val gap = positions.zipWithNext().maxBy { it.second - it.first }
    val position = (gap.first + gap.second) / 2
    val high = stops.indexOfFirst { it.position >= position }
    val color = when {
        high == -1 -> stops.last().color
        high == 0 -> stops.first().color
        else -> {
            val a = stops[high - 1]; val b = stops[high]
            GradientInterpolation.color(a.color, b.color, (position - a.position) / (b.position - a.position))
        }
    }
    return (stops + GradientStop(color, position)).sortedBy { it.position }
}
