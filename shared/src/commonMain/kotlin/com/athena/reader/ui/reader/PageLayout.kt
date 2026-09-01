package com.athena.reader.ui.reader

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.athena.reader.domain.model.Section

/** One screen of a section, as source offsets. [end] is exclusive. */
internal data class ReaderPage(
    val sectionIndex: Int,
    val start: Int,
    val end: Int,
)

internal fun paginateBook(
    sections: List<Section>,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
): List<ReaderPage> {
    if (sections.isEmpty()) return emptyList()
    return sections.flatMapIndexed { index, section ->
        paginateSection(section.content, index, measurer, style, widthPx, heightPx)
    }
}

internal fun paginateSection(
    text: String,
    sectionIndex: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
): List<ReaderPage> {
    if (text.isEmpty()) return listOf(ReaderPage(sectionIndex, 0, 0))
    if (widthPx <= 16 || heightPx <= 16) return listOf(ReaderPage(sectionIndex, 0, text.length))

    val pages = ArrayList<ReaderPage>()
    var start = 0
    var safety = 0
    while (start < text.length && safety < 8_000) {
        safety++
        val remaining = text.substring(start)
        val layout = measurer.measure(
            text = remaining,
            style = style,
            constraints = Constraints(maxWidth = widthPx, maxHeight = heightPx),
            overflow = TextOverflow.Clip,
        )
        val fitted = charsThatFit(layout, remaining.length, heightPx)
        val rawEnd = (start + fitted).coerceAtMost(text.length)
        val end = snapPageEnd(text, start, rawEnd)
        pages += ReaderPage(sectionIndex, start, end)
        if (end <= start) break
        start = end
    }
    return pages.ifEmpty { listOf(ReaderPage(sectionIndex, 0, text.length)) }
}

internal fun snapPageEnd(text: String, start: Int, rawEnd: Int): Int {
    val end = rawEnd.coerceIn((start + 1).coerceAtMost(text.length), text.length)
    if (end >= text.length) return text.length
    if (text[end - 1].isWhitespace() || text[end].isWhitespace()) return end
    val minKeep = start + (end - start) / 2
    var index = end - 1
    while (index > minKeep) {
        if (text[index].isWhitespace()) return index + 1
        index--
    }
    return end
}

internal fun pageIndexFor(pages: List<ReaderPage>, sectionIndex: Int, charOffset: Int): Int {
    val hit = pages.indexOfFirst { page ->
        page.sectionIndex == sectionIndex && charOffset >= page.start && charOffset < page.end
    }
    if (hit >= 0) return hit
    val sectionHit = pages.indexOfFirst { it.sectionIndex == sectionIndex }
    return sectionHit.coerceAtLeast(0)
}

private fun charsThatFit(layout: TextLayoutResult, remainingLength: Int, heightPx: Int): Int {
    if (layout.lineCount <= 0) return remainingLength.coerceAtLeast(1)
    val overflow = layout.didOverflowHeight || layout.size.height > heightPx
    if (!overflow) return remainingLength.coerceAtLeast(1)
    var last = layout.lineCount - 1
    while (last > 0 && layout.getLineBottom(last) > heightPx) last--
    return layout.getLineEnd(last, visibleEnd = true).coerceAtLeast(1)
}
