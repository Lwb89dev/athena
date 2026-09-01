package com.athena.reader.data.session

import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.model.Coordinate
import kotlin.math.roundToInt

/**
 * How the reader lays out a book.
 *
 * Modelled on libreadview (MIT): a horizontal page-turn versus a vertical
 * stack of pages you scroll. We already had the flip; scroll is the other
 * classic e-reader motion.
 */
enum class ReaderTurnMode { Paged, Scroll }

enum class ReaderFontKind { Garamond, Serif, Sans, Mono }

data class ReaderPrefs(
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val zoomPercent: Int = DEFAULT_ZOOM,
    val font: ReaderFontKind = ReaderFontKind.Garamond,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val turnMode: ReaderTurnMode = ReaderTurnMode.Paged,
    val marginDp: Int = DEFAULT_MARGIN,
    val lastBookNaddr: String? = null,
) {
    val effectiveFontSize: Int
        get() = (fontSize * zoomPercent / 100f).roundToInt().coerceIn(MIN_EFFECTIVE_SIZE, MAX_EFFECTIVE_SIZE)

    val lastBook: Coordinate?
        get() = lastBookNaddr?.let(Nip19::decodeNaddr)

    fun withFontSize(value: Int) = copy(fontSize = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))

    fun withZoom(percent: Int) = copy(zoomPercent = percent.coerceIn(MIN_ZOOM, MAX_ZOOM))

    fun withLineHeight(value: Float) = copy(lineHeight = value.coerceIn(MIN_LINE_HEIGHT, MAX_LINE_HEIGHT))

    fun withMargin(value: Int) = copy(marginDp = value.coerceIn(MIN_MARGIN, MAX_MARGIN))

    fun withLastBook(coordinate: Coordinate?) = copy(
        lastBookNaddr = coordinate?.let(Nip19::encodeNaddr),
    )

    companion object {
        const val MIN_FONT_SIZE = 14
        const val MAX_FONT_SIZE = 32
        const val DEFAULT_FONT_SIZE = 19
        const val MIN_ZOOM = 80
        const val MAX_ZOOM = 160
        const val DEFAULT_ZOOM = 100
        const val MIN_LINE_HEIGHT = 1.2f
        const val MAX_LINE_HEIGHT = 2.2f
        const val DEFAULT_LINE_HEIGHT = 1.65f
        const val MIN_MARGIN = 12
        const val MAX_MARGIN = 40
        const val DEFAULT_MARGIN = 20
        const val MIN_EFFECTIVE_SIZE = 12
        const val MAX_EFFECTIVE_SIZE = 48
    }
}
