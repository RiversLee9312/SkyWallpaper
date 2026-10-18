package com.example.skywallpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
internal fun WallpaperConfigurationScreen(model: WallpaperTransferModel, onBack: () -> Unit, onImported: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val busy = model.busy
    val pending = model.pending
    val message = model.message?.let { stringResource(it) }
    LaunchedEffect(message) { if (message != null) { snackbar.showSnackbar(message); model.clearMessage() } }
    LaunchedEffect(model.importCount) { if (model.importCount > 0) onImported() }
    BackHandler(busy) { }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.export(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.prepare(uri)
    }
    Scaffold(topBar = { WallpaperTopBar(stringResource(R.string.wallpaper_config_title), { if (!busy) onBack() }) },
        snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.wallpaper_config_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SettingsRow(title = stringResource(R.string.export_wallpaper_config), icon = R.drawable.ic_symbol_file_export,
                    position = 0, count = 2, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = if (!busy && pending == null) ({ export.launch("SkyWallpaper-${LocalDate.now()}.zip") }) else null)
                SettingsRow(title = stringResource(R.string.import_wallpaper_config), icon = R.drawable.ic_symbol_file_open,
                    position = 1, count = 2, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = if (!busy && pending == null) ({ import.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) else null)
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.config_processing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    pending?.let {
        AlertDialog(onDismissRequest = model::cancel,
            title = { Text(stringResource(R.string.config_import_title)) },
            text = { Text(stringResource(R.string.config_import_replace)) },
            confirmButton = { TextButton(enabled = !busy, onClick = model::confirm) { Text(stringResource(R.string.config_import_action)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = model::cancel) { Text(stringResource(R.string.cancel)) } })
    }
}
