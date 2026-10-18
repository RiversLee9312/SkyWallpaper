package com.example.skywallpaper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.annotation.DrawableRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Segmented settings rows follow the Android 16 Settings spacing and corner hierarchy. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SkySettingsScreen(
    automatic: Boolean,
    selected: SkyPhase,
    locationSummary: String,
    updatedSummary: String?,
    locationStatus: String,
    permissionSummary: String,
    backgroundAllowed: Boolean,
    onBack: () -> Unit,
    onAutomaticChanged: (Boolean) -> Unit,
    onFixPreview: () -> Unit,
    onRefresh: () -> Unit,
    onManualLocation: () -> Unit,
    onBackgroundPermission: () -> Unit,
    onSystemPermissions: () -> Unit,
    onLanguage: () -> Unit,
    onLicenses: () -> Unit,
    wallpaperSummary: String,
    onWallpapers: () -> Unit,
    wallpaperThumbnail: @Composable (Modifier) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < .5f
    val background = if (dark) colors.surface else colors.surfaceContainer
    val rowColor = if (dark) colors.surfaceContainer else colors.surfaceContainerLowest
    var info by rememberSaveable { mutableStateOf<Int?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes(), modifier = Modifier.padding(start = 16.dp, end = 12.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
                expandedHeight = 80.dp,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup(stringResource(R.string.wallpaper_behavior)) {
                    val modeShapes = ListItemDefaults.segmentedShapes(0, 3)
                    SegmentedListItem(
                        checked = automatic, onCheckedChange = onAutomaticChanged,
                        shapes = modeShapes.copy(selectedShape = modeShapes.shape),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).semantics { role = Role.Switch },
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = rowColor, selectedContainerColor = colors.primaryContainer,
                            selectedContentColor = colors.onPrimaryContainer, selectedSupportingContentColor = colors.onPrimaryContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        leadingContent = { SettingsIcon(R.drawable.ic_symbol_routine) },
                        supportingContent = { Text(stringResource(R.string.follow_description)) },
                        trailingContent = {
                            Switch(checked = automatic, onCheckedChange = null, thumbContent = {
                                Icon(painterResource(if (automatic) R.drawable.ic_check else R.drawable.ic_close), null, Modifier.size(16.dp))
                            })
                        },
                    ) { Text(stringResource(R.string.follow_sun), fontWeight = FontWeight.Medium) }
                    SettingsRow(
                        title = stringResource(R.string.fix_preview, stringResource(selected.titleRes)), icon = R.drawable.ic_symbol_wallpaper,
                        summary = stringResource(R.string.fixed_preview_description),
                        position = 1, count = 3, color = rowColor, onClick = onFixPreview,
                        trailing = {
                            wallpaperThumbnail(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium))
                        },
                    )
                    SettingsRow(title = stringResource(R.string.wallpaper_content), summary = wallpaperSummary, icon = R.drawable.ic_symbol_gradient,
                        position = 2, count = 3, color = rowColor, onClick = onWallpapers)
                }
            }
            item {
                SettingsGroup(stringResource(R.string.device_location)) {
                    SettingsRow(
                        title = locationSummary, summary = updatedSummary ?: stringResource(R.string.location_not_saved), icon = R.drawable.ic_symbol_location_on,
                        position = 0, count = 3, color = rowColor,
                    )
                    SettingsRow(
                        title = stringResource(R.string.refresh_gps), icon = R.drawable.ic_symbol_my_location,
                        summary = locationStatus.takeIf(String::isNotBlank),
                        position = 1, count = 3, color = rowColor, onClick = onRefresh,
                    )
                    SettingsRow(
                        title = stringResource(R.string.manual_coordinates), summary = stringResource(R.string.manual_location_description), icon = R.drawable.ic_symbol_edit_location_alt,
                        position = 2, count = 3, color = rowColor, onClick = onManualLocation,
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.permissions_section)) {
                    SettingsRow(
                        title = stringResource(R.string.allow_background), icon = R.drawable.ic_symbol_share_location,
                        summary = stringResource(if (backgroundAllowed) R.string.background_allowed_description else R.string.background_location_description),
                        position = 0, count = 2, color = rowColor, onClick = if (backgroundAllowed) onSystemPermissions else onBackgroundPermission,
                    )
                    SettingsRow(
                        title = stringResource(R.string.system_permissions), summary = permissionSummary, icon = R.drawable.ic_symbol_admin_panel_settings,
                        position = 1, count = 2, color = rowColor, onClick = onSystemPermissions,
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.general_section)) {
                    SettingsRow(
                        title = stringResource(R.string.app_language), summary = locale.getDisplayName(locale), icon = R.drawable.ic_symbol_language,
                        position = 0, count = 1, color = rowColor, onClick = onLanguage,
                    )
                }
            }
            item {
                SettingsGroup(stringResource(R.string.about_section)) {
                    SettingsRow(
                        title = stringResource(R.string.switch_rules), icon = R.drawable.ic_symbol_tune,
                        position = 0, count = 3, color = rowColor, onClick = { info = R.string.switch_rules },
                    )
                    SettingsRow(
                        title = stringResource(R.string.privacy_title), icon = R.drawable.ic_symbol_shield,
                        position = 1, count = 3, color = rowColor, onClick = { info = R.string.privacy_title },
                    )
                    SettingsRow(title = stringResource(R.string.open_source_licenses), icon = R.drawable.ic_symbol_description,
                        position = 2, count = 3, color = rowColor, onClick = onLicenses)
                }
            }
        }
    }
    info?.let { title ->
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(if (title == R.string.switch_rules) R.string.rules_description else R.string.privacy_description)) },
            confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, Modifier.padding(start = 20.dp, bottom = 10.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsRow(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int,
    position: Int,
    count: Int,
    color: Color,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shapes = ListItemDefaults.segmentedShapes(position, count,
        defaultShapes = if (count == 1) ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge) else ListItemDefaults.shapes())
    val trailingContent: (@Composable () -> Unit)? = trailing ?: if (onClick != null) {
        { Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(24.dp)) }
    } else null
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick, shapes = shapes,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = ListItemDefaults.segmentedColors(containerColor = color),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            supportingContent = summary?.let { { Text(it) } }, trailingContent = trailingContent,
            leadingContent = { SettingsIcon(icon) },
        ) { Text(title, fontWeight = FontWeight.Medium) }
    } else {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
            supportingContent = summary?.let { { Text(it) } },
            leadingContent = { SettingsIcon(icon) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clip(shapes.shape),
            colors = ListItemDefaults.colors(containerColor = color),
        )
    }
}

@Composable
private fun SettingsIcon(@DrawableRes resource: Int) {
    Icon(painterResource(resource), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
