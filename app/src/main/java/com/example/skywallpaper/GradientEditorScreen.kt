package com.example.skywallpaper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt

private val gradientSaver = Saver<SkyGradient, String>(save = { GradientCodec.encode(it) }, restore = { GradientCodec.decode(it) })

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GradientEditorScreen(phase: SkyPhase, type: GradientType, initial: SkyGradient, onBack: () -> Unit, onSave: (SkyGradient) -> Unit) {
    var draft by rememberSaveable(phase, type, stateSaver = gradientSaver) { mutableStateOf(initial) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var colorOriginal by rememberSaveable { mutableStateOf<Int?>(null) }
    var colorValid by rememberSaveable { mutableStateOf(true) }
    var geometry by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val valid = type != GradientType.LINEAR || abs(draft.start.x - draft.end.x) + abs(draft.start.y - draft.end.y) >= .01f
    val colors = MaterialTheme.colorScheme
    val stop = draft.stops[selected]
    val screen = LocalWindowInfo.current.containerSize
    val ratio = minOf(screen.width, screen.height).toFloat() / maxOf(screen.width, screen.height).coerceAtLeast(1)
    fun color(value: Int) {
        draft = draft.copy(stops = draft.stops.mapIndexed { i, item -> if (i == selected) item.copy(color = value) else item })
    }
    fun cancelColor() {
        colorOriginal?.let(::color)
        colorOriginal = null
        colorValid = true
    }
    fun updatePosition(index: Int, position: Float) {
        val min = if (index == 0) 0f else draft.stops[index - 1].position + .01f
        val max = if (index == draft.stops.lastIndex) 1f else draft.stops[index + 1].position - .01f
        if (min <= max) draft = draft.copy(stops = draft.stops.mapIndexed { i, item -> if (i == index) item.copy(position = position.coerceIn(min, max)) else item })
    }
    BackHandler(colorOriginal != null || geometry) { if (colorOriginal != null) cancelColor() else geometry = false }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(phase.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) } },
            actions = {
                IconButton(onClick = { reset = true }, enabled = colorOriginal == null) { Icon(painterResource(R.drawable.ic_symbol_history), stringResource(R.string.reset_gradient)) }
                Button(onClick = { onSave(draft) }, enabled = valid && colorValid, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(end = 12.dp), contentPadding = PaddingValues(horizontal = 20.dp)) { Text(stringResource(R.string.save_location)) }
            })
    }, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            val wide = screen.width > screen.height && maxWidth >= 600.dp
            val toolHeight = if (wide) maxHeight else maxHeight * if (colorOriginal != null || geometry) .56f else .44f
            @Composable fun preview(modifier: Modifier) {
                GradientPhotoPreview(type, draft, ratio.coerceAtLeast(.1f), modifier,
                    onStart = { draft = draft.copy(start = it) }, onEnd = { draft = draft.copy(end = it) },
                    onCenter = { draft = draft.copy(center = it) }, onPrecise = { geometry = true })
            }
            @Composable fun tools(modifier: Modifier) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow,
                    modifier = modifier.heightIn(max = toolHeight).testTag("gradient-tools")) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                        if (geometry) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.gradient_geometry), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { geometry = false }) { Text(stringResource(R.string.done)) }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(stringResource(type.titleRes), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.geometry_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                if (type == GradientType.LINEAR) {
                                    PointControls(stringResource(R.string.gradient_start), draft.start) { draft = draft.copy(start = it) }
                                    PointControls(stringResource(R.string.gradient_end), draft.end) { draft = draft.copy(end = it) }
                                } else {
                                    PointControls(stringResource(R.string.gradient_center), draft.center) { draft = draft.copy(center = it) }
                                    if (type == GradientType.RADIAL) {
                                        Text(stringResource(R.string.gradient_radius_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                        ParameterSlider(stringResource(R.string.gradient_radius), draft.radius, { draft = draft.copy(radius = it) }, range = .1f..2f)
                                    }
                                }
                                HorizontalDivider()
                                Text(stringResource(R.string.gradient_colors), style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    draft.stops.forEachIndexed { index, item ->
                                        val label = stringResource(R.string.color_stop, index + 1)
                                        FilledTonalIconToggleButton(checked = selected == index, onCheckedChange = { selected = index }) {
                                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(item.color)).semantics { contentDescription = label })
                                        }
                                    }
                                }
                                ParameterSlider(stringResource(R.string.stop_position), stop.position, { updatePosition(selected, it) },
                                    description = stringResource(R.string.color_stop, selected + 1) + ", " + stringResource(R.string.stop_position))
                            }
                        } else if (colorOriginal != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = ::cancelColor) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel)) }
                                Text(stringResource(R.string.color_stop, selected + 1), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { colorOriginal = null }, enabled = colorValid) { Text(stringResource(R.string.done)) }
                            }
                            GradientColorControls(stop.color, onValid = { colorValid = it }, onColor = ::color)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable {
                                    colorOriginal = stop.color; colorValid = true
                                }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(stop.color)).border(1.dp, colors.outlineVariant, CircleShape))
                                    Column {
                                        Text(stringResource(R.string.color_stop, selected + 1), style = MaterialTheme.typography.labelLarge)
                                        Text("#${hex(stop.color)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = {
                                    val added = addStop(draft.stops)
                                    selected = added.indexOfFirst { it !in draft.stops }
                                    draft = draft.copy(stops = added)
                                }, enabled = draft.stops.size < 6) { Icon(painterResource(R.drawable.ic_symbol_add), stringResource(R.string.add_color)) }
                                IconButton(onClick = {
                                    draft = draft.copy(stops = draft.stops.filterIndexed { i, _ -> i != selected })
                                    selected = selected.coerceAtMost(draft.stops.lastIndex)
                                }, enabled = draft.stops.size > 2) { Icon(painterResource(R.drawable.ic_symbol_delete), stringResource(R.string.remove_color)) }
                                FilledTonalIconButton(onClick = { geometry = true }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(painterResource(R.drawable.ic_symbol_tune), stringResource(R.string.gradient_geometry))
                                }
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                StopRail(draft.stops, selected, onSelect = { selected = it }, onMove = ::updatePosition)
                            }
                            if (type == GradientType.RADIAL) ParameterSlider(stringResource(R.string.gradient_radius), draft.radius,
                                { draft = draft.copy(radius = it) }, range = .1f..2f)
                            if (!valid) Text(stringResource(R.string.gradient_invalid_points), color = colors.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                preview(Modifier.weight(1f).fillMaxHeight())
                tools(Modifier.width(320.dp).fillMaxHeight())
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                preview(Modifier.weight(1f).fillMaxWidth())
                tools(Modifier.fillMaxWidth())
            }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text(stringResource(R.string.reset_gradient_title)) },
        text = { Text(stringResource(R.string.reset_gradient_body, stringResource(phase.titleRes))) },
        confirmButton = { TextButton(onClick = { draft = SkyGradient.default(phase, type); selected = 0; reset = false }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun GradientPhotoPreview(type: GradientType, draft: SkyGradient, ratio: Float, modifier: Modifier,
                                 onStart: (GradientPoint) -> Unit, onEnd: (GradientPoint) -> Unit,
                                 onCenter: (GradientPoint) -> Unit, onPrecise: () -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val height = minOf((maxWidth - 56.dp).coerceAtLeast(1.dp) / ratio, (maxHeight - 56.dp).coerceAtLeast(1.dp))
        val width = height * ratio
        val density = LocalDensity.current
        val viewport = with(density) { IntSize(width.roundToPx(), height.roundToPx()) }
        val origin = with(density) { Offset((maxWidth - width).toPx() / 2, (maxHeight - height).toPx() / 2) }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.size(width, height)) {
                GradientArtwork(type, GradientFrame.from(draft), Modifier.matchParentSize().testTag("gradient-preview")
                    .clip(MaterialTheme.shapes.medium).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    stringResource(R.string.gradient_preview))
            }
            if (viewport.width > 0 && viewport.height > 0) GeometryOverlay(type, draft, viewport, origin,
                onStart, onEnd, onCenter, onPrecise)
        }
    }
}

@Composable
private fun StopRail(stops: List<GradientStop>, selected: Int, onSelect: (Int) -> Unit, onMove: (Int, Float) -> Unit) {
    val latestStops by rememberUpdatedState(stops)
    val latestMove by rememberUpdatedState(onMove)
    val latestSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    var width by remember { mutableIntStateOf(0) }
    val thumb = with(density) { 48.dp.toPx() }
    val track = (width - thumb).coerceAtLeast(1f)
    Box(Modifier.fillMaxWidth().height(72.dp).onSizeChanged { width = it.width }) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(24.dp).align(Alignment.Center)
            .clip(CircleShape).background(Brush.horizontalGradient(*stops.map { it.position to Color(it.color) }.toTypedArray()))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        stops.forEachIndexed { index, item ->
            val description = stringResource(R.string.color_stop, index + 1)
            val position = stringResource(R.string.percent_format, (item.position * 100).roundToInt())
            Box(Modifier.absoluteOffset { IntOffset((item.position * track).roundToInt(), with(density) { 12.dp.roundToPx() }) }
                .zIndex(if (selected == index) 1f else 0f).size(48.dp)
                .pointerInput(index, track) {
                    detectDragGestures(onDragStart = { latestSelect(index) }) { change, delta ->
                        change.consume()
                        latestMove(index, latestStops[index].position + delta.x / track)
                    }
                }.clip(CircleShape).clickable { onSelect(index) }.semantics {
                    contentDescription = description
                    stateDescription = position
                    this.selected = selected == index
                    progressBarRangeInfo = ProgressBarRangeInfo(item.position, 0f..1f)
                    setProgress { latestMove(index, it); true }
                }, contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = Color(item.color), contentColor = if (Color(item.color).luminance() > .45f) Color.Black else Color.White,
                    modifier = Modifier.size(if (selected == index) 34.dp else 28.dp).border(2.dp, Color.White, CircleShape).border(1.dp, Color.Black, CircleShape)) {
                    Box(contentAlignment = Alignment.Center) { Text("${index + 1}", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun GeometryOverlay(type: GradientType, draft: SkyGradient, viewport: IntSize, origin: Offset,
                            onStart: (GradientPoint) -> Unit, onEnd: (GradientPoint) -> Unit,
                            onCenter: (GradientPoint) -> Unit, onPrecise: () -> Unit) {
    val density = LocalDensity.current
    fun actual(point: GradientPoint) = origin + Offset(point.x * viewport.width, point.y * viewport.height)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            clipRect(origin.x, origin.y, origin.x + viewport.width, origin.y + viewport.height) {
                fun guide(a: Offset, b: Offset) {
                    drawLine(Color.Black.copy(alpha = .5f), a, b, 3.dp.toPx())
                    drawLine(Color.White.copy(alpha = .9f), a, b, 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }
                if (type == GradientType.LINEAR) guide(actual(draft.start), actual(draft.end))
                if (type == GradientType.RADIAL) {
                    val radius = draft.radius * minOf(viewport.width, viewport.height)
                    drawCircle(Color.Black.copy(alpha = .35f), radius, actual(draft.center), style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = .8f), radius, actual(draft.center), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
                }
            }
        }
        @Composable fun handle(point: GradientPoint, label: String, mark: String, onMove: (GradientPoint) -> Unit) {
            val latestPoint by rememberUpdatedState(point)
            val latestMove by rememberUpdatedState(onMove)
            val x = stringResource(R.string.percent_format, (point.x * 100).roundToInt())
            val y = stringResource(R.string.percent_format, (point.y * 100).roundToInt())
            val position = actual(point)
            Box(Modifier.absoluteOffset { IntOffset((position.x - with(density) { 24.dp.toPx() }).roundToInt(), (position.y - with(density) { 24.dp.toPx() }).roundToInt()) }
                .size(48.dp).pointerInput(viewport) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        latestMove(GradientPoint((latestPoint.x + delta.x / viewport.width).coerceIn(0f, 1f),
                            (latestPoint.y + delta.y / viewport.height).coerceIn(0f, 1f)))
                    }
                }.clip(CircleShape).clickable(onClick = onPrecise).semantics {
                    contentDescription = label
                    stateDescription = "$x, $y"
                }, contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 3.dp,
                    modifier = Modifier.size(36.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (mark.isEmpty()) Icon(painterResource(R.drawable.ic_symbol_my_location), null, Modifier.size(20.dp))
                        else Text(mark, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (type == GradientType.LINEAR) {
            handle(draft.start, stringResource(R.string.gradient_start), "1", onStart)
            handle(draft.end, stringResource(R.string.gradient_end), "2", onEnd)
        } else handle(draft.center, stringResource(R.string.gradient_center), "", onCenter)
    }
}
