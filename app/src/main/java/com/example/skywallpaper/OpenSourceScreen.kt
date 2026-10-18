package com.example.skywallpaper

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.json.JSONArray

internal data class LicenseEntry(val id: String, val name: String, val license: String, val asset: String)

internal object OpenSourceLicenses {
    val project = LicenseEntry("project", "Sky Wallpaper", "Apache License 2.0", "licenses/project.txt")

    fun libraries(context: Context): List<LicenseEntry> {
        val catalog = JSONArray(context.assets.open("licenses/catalog.json").bufferedReader().use { it.readText() })
        return (0 until catalog.length()).map { index ->
            catalog.getJSONObject(index).let {
                LicenseEntry(it.getString("id"), it.getString("name"), it.getString("license"), it.getString("asset"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OpenSourceScreen(onBack: () -> Unit, onLicense: (String) -> Unit, onSource: () -> Unit) {
    val context = LocalContext.current
    val libraries = remember(context) { OpenSourceLicenses.libraries(context) }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            WallpaperTopBar(stringResource(R.string.open_source_licenses), onBack) {
                IconButton(onClick = onSource) {
                    Icon(painterResource(R.drawable.ic_symbol_code), stringResource(R.string.source_code))
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp)) {
            item { LicenseHeading(stringResource(R.string.project_license)) }
            item {
                SettingsRow(OpenSourceLicenses.project.name, OpenSourceLicenses.project.license, R.drawable.ic_symbol_description,
                    position = 0, count = 1, color = colors.surfaceContainerLow, onClick = { onLicense("project") })
                Spacer(Modifier.height(28.dp))
                LicenseHeading(stringResource(R.string.third_party_libraries))
            }
            itemsIndexed(libraries, key = { _, entry -> entry.id }) { index, entry ->
                SettingsRow(entry.name, entry.license, R.drawable.ic_symbol_code,
                    position = index, count = libraries.size, color = colors.surfaceContainerLow, onClick = { onLicense(entry.id) })
                if (index < libraries.lastIndex) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
            }
        }
    }
}

@Composable
private fun LicenseHeading(title: String) {
    Text(title, Modifier.padding(start = 20.dp, bottom = 10.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LicenseTextScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val entry = remember(context, id) {
        if (id == "project") OpenSourceLicenses.project else OpenSourceLicenses.libraries(context).first { it.id == id }
    }
    val text = remember(entry) { context.assets.open(entry.asset).bufferedReader().use { it.readText() }.replace("\r\n", "\n") }
    Scaffold(topBar = { WallpaperTopBar(entry.name, onBack) }, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        // Legal originals and Maven coordinates retain their original reading direction in RTL locales.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(text.split("\n\n")) { _, paragraph ->
                        Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
