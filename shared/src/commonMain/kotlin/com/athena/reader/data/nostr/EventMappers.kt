package com.athena.reader.data.nostr

import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.HighlightColor
import com.athena.reader.domain.model.HighlightVisibility
import com.athena.reader.domain.model.Section
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.platform.Limits

/**
 * Nostr events in, domain objects out.
 *
 * Tags that no NIP defines (marker colour, character range) are namespaced with
 * [APP_TAG_PREFIX] so other clients ignore them instead of choking on them, and
 * so a highlight made here is still a perfectly ordinary NIP-84 event elsewhere.
 */
object EventMappers {

    const val APP_TAG_PREFIX = "project_athena_"
    private const val TAG_COLOR = APP_TAG_PREFIX + "color"
    private const val TAG_RANGE = APP_TAG_PREFIX + "range"

    // ---- books -------------------------------------------------------------

    fun toBook(event: NostrEvent): Book? = when (event.kind) {
        Kinds.PUBLICATION_INDEX -> publicationIndexToBook(event)
        Kinds.LONG_FORM -> longFormToBook(event)
        else -> null
    }

    private fun publicationIndexToBook(event: NostrEvent): Book? {
        val coordinate = event.coordinate() ?: return null
        return Book(
            coordinate = coordinate,
            title = (event.tag("title") ?: coordinate.identifier).take(Limits.MAX_TITLE_CHARS),
            authorPubkey = event.pubkey,
            authorName = event.tag("author")?.take(Limits.MAX_TITLE_CHARS),
            summary = event.tag("summary")?.take(Limits.MAX_SUMMARY_CHARS),
            imageUrl = event.tag("image")?.take(500),
            publishedAt = event.tag("published_on")?.toLongOrNull() ?: event.createdAt,
            topics = event.tagValues("t").map { it.take(64) }.take(20),
            language = event.tag("l")?.take(16),
            sectionRefs = event.tagValues("a").mapNotNull(Coordinate::parse).take(Limits.MAX_SECTION_REFS),
        )
    }

    private fun longFormToBook(event: NostrEvent): Book? {
        val coordinate = event.coordinate() ?: return null
        return Book(
            coordinate = coordinate,
            title = (event.tag("title") ?: coordinate.identifier).take(Limits.MAX_TITLE_CHARS),
            authorPubkey = event.pubkey,
            summary = event.tag("summary")?.take(Limits.MAX_SUMMARY_CHARS),
            imageUrl = event.tag("image")?.take(500),
            publishedAt = event.tag("published_at")?.toLongOrNull() ?: event.createdAt,
            topics = event.tagValues("t").map { it.take(64) }.take(20),
            inlineContent = event.content.take(Limits.MAX_EVENT_CONTENT_CHARS),
        )
    }

    fun toSection(event: NostrEvent, index: Int): Section? {
        val coordinate = event.coordinate() ?: return null
        return Section(
            coordinate = coordinate,
            title = (event.tag("title") ?: coordinate.identifier).take(Limits.MAX_TITLE_CHARS),
            content = event.content.take(Limits.MAX_EVENT_CONTENT_CHARS),
            index = index,
        )
    }

    // ---- highlights --------------------------------------------------------

    fun toHighlight(event: NostrEvent): Highlight? {
        if (event.kind != Kinds.HIGHLIGHT) return null
        val sources = event.tagValues("a").mapNotNull(Coordinate::parse)
        val book = sources.firstOrNull { it.kind != Kinds.PUBLICATION_CONTENT }
            ?: sources.firstOrNull()
            ?: return null
        val range = event.tags.firstOrNull { it.size >= 3 && it[0] == TAG_RANGE }

        return Highlight(
            id = event.id,
            bookCoordinate = book,
            sectionCoordinate = sources.firstOrNull { it.kind == Kinds.PUBLICATION_CONTENT },
            text = event.content.take(Limits.MAX_EVENT_CONTENT_CHARS),
            comment = event.tag("comment"),
            context = event.tag("context"),
            startOffset = range?.get(1)?.toIntOrNull() ?: -1,
            endOffset = range?.get(2)?.toIntOrNull() ?: -1,
            color = event.tag(TAG_COLOR)?.let { name ->
                HighlightColor.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            } ?: HighlightColor.Yellow,
            visibility = HighlightVisibility.Public,
            authorPubkey = event.pubkey,
            createdAt = event.createdAt,
            published = true,
        )
    }

    /** A NIP-84 kind 9802 event other clients can read as-is. */
    fun toUnsignedEvent(highlight: Highlight, sourceAuthorPubkey: String?): UnsignedEvent =
        UnsignedEvent(
            kind = Kinds.HIGHLIGHT,
            content = highlight.text,
            tags = buildHighlightTags(highlight, sourceAuthorPubkey),
        )

    private fun buildHighlightTags(highlight: Highlight, sourceAuthorPubkey: String?): List<List<String>> =
        buildList {
            add(highlight.bookCoordinate.asTag())
            highlight.sectionCoordinate?.let { add(it.asTag()) }
            sourceAuthorPubkey?.let { add(listOf("p", it, "", "author")) }
            highlight.context?.let { add(listOf("context", it)) }
            highlight.comment?.let { add(listOf("comment", it)) }
            add(listOf(TAG_COLOR, highlight.color.name.lowercase()))
            if (highlight.hasRange) {
                add(listOf(TAG_RANGE, highlight.startOffset.toString(), highlight.endOffset.toString()))
            }
        }
}
