package com.athena.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.athena.reader.data.session.ReaderFontKind
import com.athena.reader.data.session.ReaderPrefs
import com.athena.reader.data.session.ReaderTurnMode
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.reader_font_garamond
import athena.shared.generated.resources.reader_font_mono
import athena.shared.generated.resources.reader_font_sans
import athena.shared.generated.resources.reader_font_serif
import athena.shared.generated.resources.reader_font_size
import athena.shared.generated.resources.reader_margins
import athena.shared.generated.resources.reader_paged
import athena.shared.generated.resources.reader_paged_hint
import athena.shared.generated.resources.reader_pages
import athena.shared.generated.resources.reader_scroll
import athena.shared.generated.resources.reader_scroll_hint
import athena.shared.generated.resources.reader_spacing
import athena.shared.generated.resources.reader_spacing_loose
import athena.shared.generated.resources.reader_spacing_normal
import athena.shared.generated.resources.reader_spacing_tight
import athena.shared.generated.resources.reader_type
import athena.shared.generated.resources.reader_zoom
import org.jetbrains.compose.resources.stringResource

/**
 * Classic e-reader knobs, the same set libreadview (MIT) exposes: type size,
 * typeface, line spacing, margins, and horizontal page-turn vs vertical scroll.
 */
@Composable
fun ReaderSettingsPanel(
    prefs: ReaderPrefs,
    onChange: (ReaderPrefs) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(Res.string.reader_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        StepperRow(
            label = stringResource(Res.string.reader_font_size, prefs.fontSize),
            onMinus = { onChange(prefs.withFontSize(prefs.fontSize - 1)) },
            onPlus = { onChange(prefs.withFontSize(prefs.fontSize + 1)) },
            minusEnabled = prefs.fontSize > ReaderPrefs.MIN_FONT_SIZE,
            plusEnabled = prefs.fontSize < ReaderPrefs.MAX_FONT_SIZE,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.reader_zoom, prefs.zoomPercent), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = prefs.zoomPercent.toFloat(),
            onValueChange = { onChange(prefs.withZoom(it.toInt())) },
            valueRange = ReaderPrefs.MIN_ZOOM.toFloat()..ReaderPrefs.MAX_ZOOM.toFloat(),
            steps = 7,
        )
        ChipRow(
            options = ReaderFontKind.entries.map { it to fontKindLabel(it) },
            selected = prefs.font,
            onSelect = { onChange(prefs.copy(font = it)) },
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.reader_spacing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ChipRow(
            options = listOf(
                1.35f to stringResource(Res.string.reader_spacing_tight),
                1.65f to stringResource(Res.string.reader_spacing_normal),
                2.0f to stringResource(Res.string.reader_spacing_loose),
            ),
            selected = nearestLineHeight(prefs.lineHeight),
            onSelect = { onChange(prefs.withLineHeight(it)) },
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.reader_margins, prefs.marginDp), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = prefs.marginDp.toFloat(),
            onValueChange = { onChange(prefs.withMargin(it.toInt())) },
            valueRange = ReaderPrefs.MIN_MARGIN.toFloat()..ReaderPrefs.MAX_MARGIN.toFloat(),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.reader_pages), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ChipRow(
            options = listOf(
                ReaderTurnMode.Paged to stringResource(Res.string.reader_paged),
                ReaderTurnMode.Scroll to stringResource(Res.string.reader_scroll),
            ),
            selected = prefs.turnMode,
            onSelect = { onChange(prefs.copy(turnMode = it)) },
        )
        Text(
            text = stringResource(
                when (prefs.turnMode) {
                    ReaderTurnMode.Paged -> Res.string.reader_paged_hint
                    ReaderTurnMode.Scroll -> Res.string.reader_scroll_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onMinus, enabled = minusEnabled) { Text("A−") }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onPlus, enabled = plusEnabled) { Text("A+") }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun fontKindLabel(kind: ReaderFontKind): String = stringResource(
    when (kind) {
        ReaderFontKind.Garamond -> Res.string.reader_font_garamond
        ReaderFontKind.Serif -> Res.string.reader_font_serif
        ReaderFontKind.Sans -> Res.string.reader_font_sans
        ReaderFontKind.Mono -> Res.string.reader_font_mono
    },
)

internal fun ReaderFontKind.toFamily(garamond: FontFamily): FontFamily = when (this) {
    ReaderFontKind.Garamond -> garamond
    ReaderFontKind.Serif -> FontFamily.Serif
    ReaderFontKind.Sans -> FontFamily.SansSerif
    ReaderFontKind.Mono -> FontFamily.Monospace
}

private fun nearestLineHeight(value: Float): Float = when {
    value < 1.5f -> 1.35f
    value < 1.85f -> 1.65f
    else -> 2.0f
}
