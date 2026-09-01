package com.athena.reader.data.local
import com.athena.reader.platform.nowMillis

import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.HighlightColor
import com.athena.reader.domain.model.HighlightVisibility
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.domain.model.Section
import com.athena.reader.nostr.model.Coordinate

private const val LIST_SEPARATOR = "\n"

fun Book.toEntity(now: Long = nowMillis()) = BookEntity(
    coordinate = coordinate.asString(),
    title = title,
    authorPubkey = authorPubkey,
    authorName = authorName,
    summary = summary,
    imageUrl = imageUrl,
    publishedAt = publishedAt,
    topics = topics.joinToString(LIST_SEPARATOR),
    language = language,
    sectionRefs = sectionRefs.joinToString(LIST_SEPARATOR) { it.asString() },
    inlineContent = inlineContent,
    cachedAt = now,
    imported = imported,
)

fun BookEntity.toDomain(): Book? {
    val parsed = Coordinate.parse(coordinate) ?: return null
    return Book(
        coordinate = parsed,
        title = title,
        authorPubkey = authorPubkey,
        authorName = authorName,
        summary = summary,
        imageUrl = imageUrl,
        publishedAt = publishedAt,
        topics = topics.splitList(),
        language = language,
        sectionRefs = sectionRefs.splitList().mapNotNull(Coordinate::parse),
        inlineContent = inlineContent,
        imported = imported,
    )
}

fun Section.toEntity(bookCoordinate: Coordinate) = SectionEntity(
    coordinate = coordinate.asString(),
    bookCoordinate = bookCoordinate.asString(),
    title = title,
    content = content,
    position = index,
)

fun SectionEntity.toDomain(): Section? {
    val parsed = Coordinate.parse(coordinate) ?: return null
    return Section(parsed, title, content, position)
}

fun Highlight.toEntity() = HighlightEntity(
    id = id,
    bookCoordinate = bookCoordinate.asString(),
    sectionCoordinate = sectionCoordinate?.asString(),
    text = text,
    comment = comment,
    context = context,
    startOffset = startOffset,
    endOffset = endOffset,
    color = color.name,
    visibility = visibility.name,
    authorPubkey = authorPubkey,
    createdAt = createdAt,
    published = published,
)

fun HighlightEntity.toDomain(): Highlight? {
    val book = Coordinate.parse(bookCoordinate) ?: return null
    return Highlight(
        id = id,
        bookCoordinate = book,
        sectionCoordinate = sectionCoordinate?.let(Coordinate::parse),
        text = text,
        comment = comment,
        context = context,
        startOffset = startOffset,
        endOffset = endOffset,
        color = HighlightColor.entries.firstOrNull { it.name == color } ?: HighlightColor.Yellow,
        visibility = HighlightVisibility.entries.firstOrNull { it.name == visibility }
            ?: HighlightVisibility.Public,
        authorPubkey = authorPubkey,
        createdAt = createdAt,
        published = published,
    )
}

fun ReadingProgress.toEntity(synced: Boolean) = ProgressEntity(
    bookCoordinate = bookCoordinate.asString(),
    sectionIndex = sectionIndex,
    charOffset = charOffset,
    fraction = fraction,
    updatedAt = updatedAt,
    synced = synced,
)

fun ProgressEntity.toDomain(): ReadingProgress? {
    val book = Coordinate.parse(bookCoordinate) ?: return null
    return ReadingProgress(book, sectionIndex, charOffset, updatedAt, fraction)
}

private fun String.splitList(): List<String> =
    if (isEmpty()) emptyList() else split(LIST_SEPARATOR).filter(String::isNotBlank)
