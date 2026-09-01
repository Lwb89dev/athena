package com.athena.reader.domain.model

import com.athena.reader.nostr.model.Coordinate
import kotlin.test.Test
import kotlin.test.assertTrue

class HighlightTest {

    private val book = Coordinate(30040, "a".repeat(64), "book")
    private val chapter1 = Coordinate(30041, "a".repeat(64), "book-1")
    private val chapter2 = Coordinate(30041, "a".repeat(64), "book-2")

    @Test
    fun `a tagged highlight only paints on its own chapter`() {
        val mark = sample(section = chapter1)
        assertTrue(mark.belongsTo(chapter1, singleSection = false))
        assertTrue(!mark.belongsTo(chapter2, singleSection = false))
    }

    @Test
    fun `an untagged highlight only paints on a one-section work`() {
        val mark = sample(section = null)
        assertTrue(mark.belongsTo(chapter1, singleSection = true))
        assertTrue(!mark.belongsTo(chapter1, singleSection = false))
    }

    private fun sample(section: Coordinate?) = Highlight(
        id = "x",
        bookCoordinate = book,
        sectionCoordinate = section,
        text = "quote",
        startOffset = 0,
        endOffset = 5,
        authorPubkey = book.pubkey,
        createdAt = 0,
    )
}
