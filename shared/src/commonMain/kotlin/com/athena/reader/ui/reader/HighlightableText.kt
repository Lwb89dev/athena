package com.athena.reader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.athena.reader.domain.model.Highlight
import com.athena.reader.ui.theme.LocalIsDarkTheme
import com.athena.reader.ui.theme.LocalReaderTypography
import com.athena.reader.ui.theme.surface

/**
 * The reading surface.
 *
 * Compose's `SelectionContainer` renders a selection but will not tell you what
 * was selected, and a highlighter needs the character range. A read-only
 * `BasicTextField` does expose it, as `TextFieldValue.selection`, while still
 * behaving like a block of prose — no caret, native selection handles, the
 * platform's own text toolbar. That is why the text lives in a text field
 * rather than a `Text`.
 *
 * Offsets reported upward are always *source* offsets, never rendered ones, so
 * a highlight survives changes to how markup is displayed.
 */
@Composable
fun HighlightableText(
    content: String,
    markup: Markup,
    highlights: List<Highlight>,
    onSelectionChange: (IntRange?, String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
) {
    val darkTheme = LocalIsDarkTheme.current
    val typography = LocalReaderTypography.current

    // Rendering walks the whole section, and sections are long: do it only when
    // the text or the markup actually changes, never on every recomposition.
    val rendered = remember(content, markup) { MarkupRenderer.render(content, markup) }

    val styled = remember(rendered, highlights, darkTheme) {
        withHighlights(rendered, highlights, darkTheme)
    }

    var value by remember(rendered) { mutableStateOf(TextFieldValue(styled)) }

    LaunchedEffect(value.selection, rendered) {
        val selection = value.selection
        if (selection.collapsed) {
            onSelectionChange(null, "")
            return@LaunchedEffect
        }
        val source = rendered.toSourceRange(selection.min, selection.max)
        if (source == null) {
            onSelectionChange(null, "")
            return@LaunchedEffect
        }
        val end = (source.last + 1).coerceAtMost(content.length)
        onSelectionChange(source, content.substring(source.first.coerceIn(0, end), end))
    }

    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value.copy(annotatedString = styled),
            onValueChange = { updated ->
                // Read-only: keep the text, take only the new selection.
                value = value.copy(selection = updated.selection)
            },
            readOnly = true,
            textStyle = typography.bodyStyle.merge(
                LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            ),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp),
        )
    }
}

/**
 * Paints markers onto the rendered text. Source ranges are translated and then
 * clamped: a highlight may have been made against an older revision of the
 * section that was longer than the one we just downloaded.
 */
private fun withHighlights(
    rendered: RenderedText,
    highlights: List<Highlight>,
    darkTheme: Boolean,
): AnnotatedString = buildAnnotatedString {
    append(rendered.text)

    highlights.filter(Highlight::hasRange).forEach { highlight ->
        val range = rendered.toRenderedRange(highlight.startOffset, highlight.endOffset)
            ?: return@forEach
        addStyle(
            SpanStyle(background = highlight.color.surface(darkTheme)),
            range.first,
            range.last + 1,
        )
    }
}
