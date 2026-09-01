package com.athena.reader.domain.model

import com.athena.reader.nostr.model.Coordinate

/** Marker colours offered in the reader. */
enum class HighlightColor { Yellow, Green, Blue, Pink, Purple }

/** Public highlights are NIP-84 events; private ones live encrypted in a NIP-51 set. */
enum class HighlightVisibility { Public, Private }

/**
 * A highlighted passage — NIP-84 (kind 9802). The event content *is* the quoted
 * text; everything else (which book, which section, our note) travels in tags.
 */
data class Highlight(
    /** Event id for published highlights; a local uuid until then. */
    val id: String,
    val bookCoordinate: Coordinate,
    val sectionCoordinate: Coordinate?,
    val text: String,
    val comment: String? = null,
    /** Surrounding sentence, so the passage can be relocated after an edit. */
    val context: String? = null,
    /** Character range inside the section, used to paint the marker back on. */
    val startOffset: Int = -1,
    val endOffset: Int = -1,
    val color: HighlightColor = HighlightColor.Yellow,
    val visibility: HighlightVisibility = HighlightVisibility.Public,
    val authorPubkey: String,
    val createdAt: Long,
    /** False while the event is still queued for the signer. */
    val published: Boolean = false,
) {
    val hasRange: Boolean get() = startOffset >= 0 && endOffset > startOffset

    /** Markers without a section tag belong on a one-section work, nowhere else. */
    fun belongsTo(section: Coordinate, singleSection: Boolean): Boolean {
        val tagged = sectionCoordinate ?: return singleSection
        return tagged == section
    }
}
