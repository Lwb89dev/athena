package com.athena.reader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/** Which markup a section's text is written in. */
enum class Markup { AsciiDoc, Markdown, Plain }

/**
 * Rendered text plus the mapping back to the source offsets.
 *
 * The mapping is the whole point. Highlights are stored as offsets into the
 * *source*, so if rendering silently swallowed `== ` and `**` every existing
 * marker would land in the wrong place the moment rendering changed. Producing
 * the translation alongside the text keeps old highlights anchored.
 */
class RenderedText(
    val text: AnnotatedString,
    private val sourceToRendered: IntArray,
    private val renderedToSource: IntArray,
) {
    val length: Int get() = text.length

    fun toRenderedOffset(sourceOffset: Int): Int =
        sourceToRendered.getOrElse(sourceOffset.coerceIn(0, sourceToRendered.lastIndex)) { 0 }

    fun toSourceOffset(renderedOffset: Int): Int =
        renderedToSource.getOrElse(renderedOffset.coerceIn(0, renderedToSource.lastIndex)) { 0 }

    /**
     * Translates a rendered selection into a source range.
     *
     * The end needs care: it is exclusive, so mapping it directly lands on the
     * *next* emitted character and silently swallows any markup between the two
     * — a selection of `emphasis` would come back as `emphasis*`. Taking the
     * last included character and adding one keeps the range tight.
     */
    fun toSourceRange(renderedStart: Int, renderedEnd: Int): IntRange? {
        if (renderedEnd <= renderedStart) return null
        val start = toSourceOffset(renderedStart)
        val end = toSourceOffset(renderedEnd - 1) + 1
        return if (end <= start) null else start until end
    }

    /** Translates a source range into rendered coordinates, clamped to the text. */
    fun toRenderedRange(sourceStart: Int, sourceEnd: Int): IntRange? {
        val start = toRenderedOffset(sourceStart).coerceIn(0, length)
        val end = toRenderedOffset(sourceEnd).coerceIn(start, length)
        return if (end <= start) null else start until end
    }
}

/**
 * A deliberately small markup renderer for NKBIP-01 AsciiDoc and NIP-23 Markdown.
 *
 * It covers what actually appears in published books — headings, bold, italic,
 * inline code, block quotes, bullet lists — and leaves anything else as plain
 * text rather than mangling it. Full AsciiDoc is a project of its own; showing
 * raw `==` to a reader is the part worth fixing now.
 *
 * Structured line by line, and styles are applied as explicit ranges rather than
 * push/pop, so an unbalanced `*` in a source document cannot desynchronise the
 * builder and crash the reader.
 */
object MarkupRenderer {

    fun detect(content: String, isAsciiDoc: Boolean): Markup = when {
        isAsciiDoc -> Markup.AsciiDoc
        Regex("^#{1,6} ", RegexOption.MULTILINE).containsMatchIn(content) -> Markup.Markdown
        content.contains("**") -> Markup.Markdown
        else -> Markup.Plain
    }

    fun render(content: String, markup: Markup): RenderedText {
        if (markup == Markup.Plain) return identity(content)

        val builder = AnnotatedString.Builder()
        val sourceToRendered = IntArray(content.length + 1)
        val renderedToSource = ArrayList<Int>(content.length)

        var lineStart = 0
        while (lineStart <= content.length) {
            val newline = content.indexOf('\n', lineStart).takeIf { it >= 0 } ?: content.length
            renderLine(content, lineStart, newline, markup, builder, sourceToRendered, renderedToSource)

            if (newline == content.length) break
            sourceToRendered[newline] = builder.length
            builder.append('\n')
            renderedToSource.add(newline)
            lineStart = newline + 1
        }

        sourceToRendered[content.length] = builder.length
        renderedToSource.add(content.length)
        return RenderedText(builder.toAnnotatedString(), sourceToRendered, renderedToSource.toIntArray())
    }

    private fun renderLine(
        content: String,
        start: Int,
        end: Int,
        markup: Markup,
        builder: AnnotatedString.Builder,
        sourceToRendered: IntArray,
        renderedToSource: MutableList<Int>,
    ) {
        val renderedLineStart = builder.length
        val prefix = parsePrefix(content, start, end, markup)

        for (offset in start until start + prefix.consumed) sourceToRendered[offset] = builder.length

        prefix.bullet?.let { bullet ->
            builder.append(bullet)
            repeat(bullet.length) { renderedToSource.add(start) }
        }

        renderInline(content, start + prefix.consumed, end, markup, builder, sourceToRendered, renderedToSource)

        prefix.style?.let { builder.addStyle(it, renderedLineStart, builder.length) }
    }

    private class LinePrefix(val consumed: Int, val style: SpanStyle?, val bullet: String? = null)

    private fun parsePrefix(content: String, start: Int, end: Int, markup: Markup): LinePrefix {
        val headingMarker = if (markup == Markup.AsciiDoc) '=' else '#'

        var level = 0
        while (start + level < end && content[start + level] == headingMarker) level++
        if (level in 1..6 && start + level < end && content[start + level] == ' ') {
            return LinePrefix(level + 1, headingStyle(level))
        }

        if (content.startsWith("> ", start) && start + 2 <= end) {
            return LinePrefix(2, QUOTE_STYLE)
        }

        if ((content.startsWith("* ", start) || content.startsWith("- ", start)) && start + 2 <= end) {
            return LinePrefix(2, null, bullet = "•  ")
        }
        return LinePrefix(0, null)
    }

    /**
     * Emphasis inside one line. Markers are only treated as markers when they
     * come in pairs on the same line; a lone `*` is emitted as itself.
     */
    private fun renderInline(
        content: String,
        start: Int,
        end: Int,
        markup: Markup,
        builder: AnnotatedString.Builder,
        sourceToRendered: IntArray,
        renderedToSource: MutableList<Int>,
    ) {
        val bold = if (markup == Markup.AsciiDoc) "*" else "**"
        val markers = listOf(
            bold to SpanStyle(fontWeight = FontWeight.Bold),
            "_" to SpanStyle(fontStyle = FontStyle.Italic),
            "`" to SpanStyle(fontFamily = FontFamily.Monospace),
        )
        val open = HashMap<String, Int>()

        var index = start
        while (index < end) {
            sourceToRendered[index] = builder.length

            val marker = markers.firstOrNull { (token, _) ->
                content.startsWith(token, index) &&
                    index + token.length <= end &&
                    (open.containsKey(token) || hasClosingToken(content, index + token.length, end, token))
            }

            if (marker == null) {
                builder.append(content[index])
                renderedToSource.add(index)
                index++
                continue
            }

            val (token, style) = marker
            val opened = open.remove(token)
            if (opened == null) open[token] = builder.length else builder.addStyle(style, opened, builder.length)
            index += token.length
        }
    }

    private fun hasClosingToken(content: String, from: Int, end: Int, token: String): Boolean {
        var index = from
        while (index + token.length <= end) {
            if (content.startsWith(token, index)) return true
            index++
        }
        return false
    }

    private fun headingStyle(level: Int) = SpanStyle(
        fontWeight = FontWeight.Bold,
        fontSize = when (level) {
            1 -> 1.6f.em
            2 -> 1.4f.em
            3 -> 1.2f.em
            else -> 1.1f.em
        },
    )

    private val QUOTE_STYLE = SpanStyle(fontStyle = FontStyle.Italic)

    private fun identity(content: String): RenderedText {
        val mapping = IntArray(content.length + 1) { it }
        return RenderedText(AnnotatedString(content), mapping, mapping.copyOf())
    }
}
