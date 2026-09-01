package com.athena.reader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The local cache. Relays are the source of truth; this exists so the library
 * and your own highlights open instantly and keep working offline.
 */
@Entity(tableName = "books")
data class BookEntity(
    /** `kind:pubkey:d` — stable across edits. */
    @PrimaryKey val coordinate: String,
    val title: String,
    val authorPubkey: String,
    val authorName: String?,
    val summary: String?,
    val imageUrl: String?,
    val publishedAt: Long?,
    val topics: String,
    val language: String?,
    /** Section coordinates, newline separated, in reading order. */
    val sectionRefs: String,
    val inlineContent: String?,
    val cachedAt: Long,
    /** Local file, not a relay discovery. The uploader's npub must never be shown. */
    val imported: Boolean = false,
)

@Entity(tableName = "sections")
data class SectionEntity(
    @PrimaryKey val coordinate: String,
    val bookCoordinate: String,
    val title: String,
    val content: String,
    val position: Int,
)

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookCoordinate: String,
    val sectionCoordinate: String?,
    val text: String,
    val comment: String?,
    val context: String?,
    val startOffset: Int,
    val endOffset: Int,
    val color: String,
    val visibility: String,
    val authorPubkey: String,
    val createdAt: Long,
    /** False while the event is still waiting on the signer or a relay ack. */
    val published: Boolean,
)

@Entity(tableName = "reading_progress")
data class ProgressEntity(
    @PrimaryKey val bookCoordinate: String,
    val sectionIndex: Int,
    val charOffset: Int,
    val fraction: Float,
    val updatedAt: Long,
    /** False until the NIP-78 event has gone out, so we can retry after offline. */
    val synced: Boolean,
)

/**
 * Per-slot sync bookkeeping, kept out of the payload so it survives a failed
 * decrypt. `lastSeenAt` is the anti-rollback high-water mark; `lastPublishedAt`
 * keeps replaceable-event ordering monotonic despite hour-rounded timestamps.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val slot: String,
    val lastSeenAt: Long,
    val lastPublishedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val bookCoordinate: String,
    val isPrivate: Boolean,
    val addedAt: Long,
)
