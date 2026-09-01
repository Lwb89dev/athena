package com.athena.reader.domain.model

import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Kinds

/**
 * A readable work. Built either from an NKBIP-01 index (kind 30040) with its
 * sections, or from a single NIP-23 article (kind 30023) promoted to a one-section
 * book so the reader only ever deals with one shape.
 */
data class Book(
    val coordinate: Coordinate,
    val title: String,
    val authorPubkey: String,
    val authorName: String? = null,
    val summary: String? = null,
    val imageUrl: String? = null,
    val publishedAt: Long? = null,
    val topics: List<String> = emptyList(),
    val language: String? = null,
    /** Section coordinates in reading order. Empty for a single-part work. */
    val sectionRefs: List<Coordinate> = emptyList(),
    /** Set when the whole text arrived inline (NIP-23). */
    val inlineContent: String? = null,
    /** True when this work was added from a local file, not discovered on relays. */
    val imported: Boolean = false,
) {
    val sectionCount: Int get() = if (sectionRefs.isEmpty()) 1 else sectionRefs.size
    val isSinglePart: Boolean get() = sectionRefs.isEmpty()
    val isLongFormArticle: Boolean get() = coordinate.kind == Kinds.LONG_FORM
}

/** One chapter's worth of text. */
data class Section(
    val coordinate: Coordinate,
    val title: String,
    /** AsciiDoc for kind 30041, Markdown for kind 30023. */
    val content: String,
    val index: Int,
)

/** Where the reader left off, per book. Synced through NIP-78. */
data class ReadingProgress(
    val bookCoordinate: Coordinate,
    val sectionIndex: Int,
    /** Character offset inside the section — resolution-independent, unlike a pixel scroll. */
    val charOffset: Int,
    val updatedAt: Long,
    val fraction: Float = 0f,
) {
    val percent: Int get() = (fraction * 100).toInt().coerceIn(0, 100)
}
