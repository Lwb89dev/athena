package com.athena.reader.data.repository

import com.athena.reader.data.local.ProgressDao
import com.athena.reader.data.local.toDomain
import com.athena.reader.data.local.toEntity
import com.athena.reader.data.sync.EncryptedSync
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.nostr.crypto.BlindedPath
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.nowSeconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What goes inside the encrypted NIP-78 payload. */
@Serializable
private data class ProgressPayload(
    val section: Int = 0,
    val offset: Int = 0,
    val fraction: Float = 0f,
    val updatedAt: Long = 0,
    val cleared: Boolean = false,
)

/**
 * Cross-device reading position.
 *
 * Where someone is in a book is nobody else's business, so this never leaves
 * the device as anything but ciphertext at an address a relay cannot interpret.
 * The slot is `HMAC(secret, "progress:<book coordinate>")`: one opaque hex
 * string per book, with no way back to the book and no way to correlate the
 * same book across two users.
 *
 * That matters more here than for a settings blob. A readable slot address
 * would let any relay answer "who is reading this book" and "what is this npub
 * reading" — the reading list is the sensitive part, not the page number.
 *
 * Writes are deliberately *not* fired on every scroll: [saveLocal] updates the
 * local row constantly, [syncPending] publishes only when the reader is closed.
 */
class ProgressRepository(
    private val progressDao: ProgressDao,
    private val encryptedSync: EncryptedSync,
    private val signerManager: SignerManager,
    private val json: Json,
) {
    fun observe(coordinate: Coordinate): Flow<ReadingProgress?> =
        progressDao.observe(coordinate.asString()).map { it?.toDomain() }

    fun observeContinueReading(limit: Int = 40): Flow<List<ReadingProgress>> =
        progressDao.observeRecent(limit).map { rows -> rows.mapNotNull { it.toDomain() } }

    /** Cheap, called on every page turn. */
    suspend fun saveLocal(progress: ReadingProgress) {
        progressDao.upsert(progress.toEntity(synced = false))
    }

    /** Expensive, called when the reader is closed or backgrounded. */
    suspend fun syncPending() {
        if (!signerManager.current.value.canSign) return
        val secret = encryptedSync.secret() ?: return

        progressDao.unsynced().forEach { row ->
            row.toDomain()?.let { publish(it, secret) }
        }
    }

    /** Pulls the position saved by another device, keeping whichever is newer. */
    suspend fun pull(coordinate: Coordinate): ReadingProgress? {
        val secret = encryptedSync.secret() ?: return null
        val slot = BlindedPath.progress(secret, coordinate.asString())

        val plaintext = encryptedSync.get(slot) ?: return null
        val payload = runCatching {
            json.decodeFromString(ProgressPayload.serializer(), plaintext)
        }.getOrNull() ?: return null

        if (payload.cleared) {
            val existing = progressDao.get(coordinate.asString())?.toDomain()
            if (existing != null && existing.updatedAt >= payload.updatedAt) return existing
            progressDao.delete(coordinate.asString())
            return null
        }

        val remote = ReadingProgress(
            bookCoordinate = coordinate,
            sectionIndex = payload.section,
            charOffset = payload.offset,
            updatedAt = payload.updatedAt,
            fraction = payload.fraction,
        )
        val local = progressDao.get(coordinate.asString())?.toDomain()
        if (local != null && local.updatedAt >= remote.updatedAt) return local

        progressDao.upsert(remote.toEntity(synced = true))
        return remote
    }

    suspend fun forget(coordinate: Coordinate) {
        progressDao.delete(coordinate.asString())
        tombstone(coordinate)
    }

    suspend fun forgetWhere(match: (Coordinate) -> Boolean) {
        progressDao.getAll()
            .mapNotNull { Coordinate.parse(it.bookCoordinate) }
            .filter(match)
            .forEach { forget(it) }
    }

    private suspend fun tombstone(coordinate: Coordinate) {
        if (!signerManager.current.value.canSign) return
        val secret = encryptedSync.secret() ?: return
        val payload = json.encodeToString(
            ProgressPayload.serializer(),
            ProgressPayload(cleared = true, updatedAt = nowSeconds()),
        )
        encryptedSync.put(BlindedPath.progress(secret, coordinate.asString()), payload)
    }

    private suspend fun publish(progress: ReadingProgress, secret: ByteArray) {
        val payload = json.encodeToString(
            ProgressPayload.serializer(),
            ProgressPayload(
                section = progress.sectionIndex,
                offset = progress.charOffset,
                fraction = progress.fraction,
                updatedAt = progress.updatedAt,
            ),
        )
        val slot = BlindedPath.progress(secret, progress.bookCoordinate.asString())
        if (encryptedSync.put(slot, payload)) {
            progressDao.upsert(progress.toEntity(synced = true))
        }
    }
}
