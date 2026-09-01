package com.athena.reader.data.repository

import com.athena.reader.data.local.FavoriteDao
import com.athena.reader.data.local.FavoriteEntity
import com.athena.reader.data.local.SyncStateDao
import com.athena.reader.data.local.SyncStateEntity
import com.athena.reader.data.sync.EncryptedSync
import com.athena.reader.nostr.crypto.BlindedPath
import com.athena.reader.nostr.model.AppNamespace
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Log
import com.athena.reader.platform.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The favourites shelf, split down the middle because the two halves want
 * opposite things.
 *
 * **Public** favourites are a NIP-51 bookmark set (kind 30003) with ordinary
 * `a` tags under a readable `d` tag. Any nostr client can render the shelf, and
 * that is exactly what the user asked for when they made it public. There is
 * nothing to hide here and pretending otherwise would only break interop.
 *
 * **Private** favourites are ciphertext in a blinded [EncryptedSync] slot. Not
 * merely encrypted inside a well-known list — a readable `d` tag would still
 * tell a relay that this npub keeps private favourites, how many bytes of them,
 * and when the list last changed.
 *
 * So "public or private" is not a display flag. It decides which of two
 * genuinely different storage mechanisms an entry lands in.
 */
class FavoriteRepository(
    private val relayPool: RelayPool,
    private val favoriteDao: FavoriteDao,
    private val signerManager: SignerManager,
    private val encryptedSync: EncryptedSync,
    private val syncStateDao: SyncStateDao,
    private val json: Json,
) {
    fun observeAll(): Flow<List<Pair<Coordinate, Boolean>>> =
        favoriteDao.observeAll().map { rows ->
            rows.mapNotNull { row -> Coordinate.parse(row.bookCoordinate)?.to(row.isPrivate) }
        }

    fun observeIsFavorite(coordinate: Coordinate): Flow<Boolean> =
        favoriteDao.observeIsFavorite(coordinate.asString())

    suspend fun toggle(coordinate: Coordinate, isPrivate: Boolean) {
        val key = coordinate.asString()
        if (favoriteDao.isFavorite(key)) {
            favoriteDao.delete(key)
        } else {
            favoriteDao.upsert(FavoriteEntity(key, isPrivate, nowMillis()))
        }
        publishAll()
    }

    /** Republishes both halves; each is a replaceable slot with no partial update. */
    suspend fun publishAll() {
        if (!signerManager.current.value.canSign) return
        val rows = favoriteDao.getAll()

        publishPublicSet(rows.filterNot { it.isPrivate })
        publishPrivateSet(rows.filter { it.isPrivate })
    }

    /** Restores the shelf on a new device, both halves. */
    suspend fun pull() {
        pullPublicSet()
        pullPrivateSet()
    }

    private suspend fun publishPublicSet(rows: List<FavoriteEntity>) {
        val signer = signerManager.current.value
        val tags = rows.mapNotNull { Coordinate.parse(it.bookCoordinate)?.asTag() }

        // Never publish an empty public set unprompted: for a user who keeps
        // only private favourites it would announce "this npub uses Athena"
        // at a readable address, in exchange for nothing. The one exception is
        // clearing a set we previously published.
        val everPublished = syncStateDao.get(AppNamespace.FAVORITES_SET) != null
        if (tags.isEmpty() && !everPublished) return

        val unsigned = UnsignedEvent(
            kind = Kinds.BOOKMARK_SET,
            tags = buildList {
                add(listOf("d", AppNamespace.FAVORITES_SET))
                add(listOf("title", "Project Athena"))
                addAll(tags)
            },
            // Public set: the tags carry everything, the content stays empty
            // rather than holding a second, encrypted copy of the same shelf.
            content = "",
        )
        signer.sign(unsigned)
            .onSuccess { event ->
                relayPool.publish(event)
                syncStateDao.upsert(
                    SyncStateEntity(AppNamespace.FAVORITES_SET, event.createdAt, event.createdAt),
                )
            }
            .onFailure { Log.w(TAG, "public favourites not signed: ${it.message}") }
    }

    private suspend fun publishPrivateSet(rows: List<FavoriteEntity>) {
        val secret = encryptedSync.secret() ?: return
        val coordinates = rows.map(FavoriteEntity::bookCoordinate)

        val payload = json.encodeToString(ListSerializer(String.serializer()), coordinates)
        encryptedSync.put(BlindedPath.privateFavorites(secret), payload)
    }

    private suspend fun pullPublicSet() {
        val pubkey = signerManager.current.value.pubkeyHex ?: return

        val filter = Filter(
            authors = listOf(pubkey),
            kinds = listOf(Kinds.BOOKMARK_SET),
            tags = mapOf("d" to listOf(AppNamespace.FAVORITES_SET)),
            limit = 1,
        )
        val event = relayPool.fetch(listOf(filter)).maxByOrNull { it.createdAt } ?: return
        val now = nowMillis()

        event.tagValues("a").mapNotNull(Coordinate::parse).forEach { coordinate ->
            favoriteDao.upsert(FavoriteEntity(coordinate.asString(), isPrivate = false, addedAt = now))
        }
    }

    private suspend fun pullPrivateSet() {
        val secret = encryptedSync.secret() ?: return
        val plaintext = encryptedSync.get(BlindedPath.privateFavorites(secret)) ?: return

        val coordinates = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), plaintext)
        }.getOrElse { error ->
            Log.w(TAG, "private favourites are not in a shape we understand: ${error.message}")
            return
        }
        val now = nowMillis()

        coordinates.mapNotNull(Coordinate::parse).forEach { coordinate ->
            favoriteDao.upsert(FavoriteEntity(coordinate.asString(), isPrivate = true, addedAt = now))
        }
    }

    private companion object {
        const val TAG = "FavoriteRepository"
    }
}
