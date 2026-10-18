package com.example.skywallpaper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Keep full translated phase labels legible, including at larger system font sizes. */
@Composable
internal fun SkyGallery(store: WallpaperStore, revision: Int, selected: SkyPhase, onSelect: (SkyPhase) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    var labelWidth = 0
    for (phase in SkyPhase.entries) {
        labelWidth = maxOf(labelWidth, textMeasurer.measure(AnnotatedString(stringResource(phase.titleRes)), labelStyle).size.width)
    }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        fun availableLabelWidth(columns: Int) = with(density) {
            ((maxWidth - 8.dp * (columns - 1)) / columns - 16.dp).toPx()
        }
        val columns = when {
            labelWidth <= availableLabelWidth(4) -> 4
            labelWidth <= availableLabelWidth(2) -> 2
            else -> 1
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkyPhase.entries.chunked(columns).forEach { phases ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    phases.forEach { phase ->
                        val description = stringResource(R.string.gallery_description, stringResource(phase.titleRes))
                        Column(Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.large)
                            .background(if (selected == phase) colors.primaryContainer else colors.surfaceContainer)
                            .selectable(selected = selected == phase, role = Role.RadioButton, onClick = { onSelect(phase) })
                            .semantics { contentDescription = description }
                            .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                WallpaperArtwork(store, phase, revision, Modifier.fillMaxWidth().height(64.dp).clip(MaterialTheme.shapes.medium))
                                if (selected == phase) Surface(Modifier.align(Alignment.TopEnd).padding(4.dp), shape = MaterialTheme.shapes.extraLarge,
                                    color = colors.primary, contentColor = colors.onPrimary) {
                                    Icon(painterResource(R.drawable.ic_check), null, Modifier.padding(3.dp).size(12.dp))
                                }
                            }
                            Text(stringResource(phase.titleRes), Modifier.padding(vertical = 6.dp), style = labelStyle, textAlign = TextAlign.Center,
                                color = if (selected == phase) colors.onPrimaryContainer else colors.onSurface)
                        }
                    }
                }
            }
        }
    }
}
