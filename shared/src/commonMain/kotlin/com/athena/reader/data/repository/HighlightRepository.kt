package com.athena.reader.data.repository

import com.athena.reader.data.local.HighlightDao
import com.athena.reader.data.local.toDomain
import com.athena.reader.data.local.toEntity
import com.athena.reader.data.nostr.EventMappers
import com.athena.reader.data.sync.EncryptedSync
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.HighlightColor
import com.athena.reader.domain.model.HighlightVisibility
import com.athena.reader.nostr.crypto.BlindedPath
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Log
import com.athena.reader.platform.nowMillis
import com.athena.reader.platform.nowSeconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * One private highlight inside the encrypted NIP-51 blob.
 *
 * The blob is NIP-44 encrypted to the user's own key, so nobody else can read
 * it — which means its shape is ours to choose, and a plain record round-trips
 * far more reliably than trying to squeeze a highlight into bare NIP-51 tags.
 */
@Serializable
private data class PrivateHighlight(
    val book: String,
    val section: String? = null,
    val text: String,
    val comment: String? = null,
    val context: String? = null,
    val start: Int = -1,
    val end: Int = -1,
    val color: String = "Yellow",
    val createdAt: Long = 0,
)

/**
 * Highlights, public and private — and the two are genuinely different things,
 * not one thing with a flag.
 *
 * **Public** ones are plain NIP-84 kind 9802 events: readable, quotable and
 * discoverable by any nostr client, which is the entire point of marking a
 * passage in public. Blinding them would be a contradiction.
 *
 * **Private** ones are ciphertext at a blinded address. They used to live in a
 * NIP-51 set under a readable `d` tag, which told any relay how many private
 * highlights a user keeps and when they last added one. They now go through
 * [EncryptedSync] like everything else private: one opaque slot, hour-rounded
 * timestamps, size-bucketed payload.
 */
class HighlightRepository(
    private val relayPool: RelayPool,
    private val highlightDao: HighlightDao,
    private val signerManager: SignerManager,
    private val encryptedSync: EncryptedSync,
    private val json: Json,
) {
    fun observeMine(pubkey: String): Flow<List<Highlight>> =
        highlightDao.observeMine(pubkey).map { rows -> rows.mapNotNull { it.toDomain() } }

    fun observeForBook(coordinate: Coordinate): Flow<List<Highlight>> =
        highlightDao.observeForBook(coordinate.asString()).map { rows -> rows.mapNotNull { it.toDomain() } }

    /**
     * Saves locally first and publishes after. The marker appears under the
     * user's finger immediately; the signer round-trip happens behind it and,
     * if it fails, the highlight stays as `published = false` for a later retry.
     */
    suspend fun create(draft: Highlight): Highlight {
        val pubkey = signerManager.current.value.pubkeyHex
            ?: return draft.copy(id = localId()).also { highlightDao.upsert(listOf(it.toEntity())) }

        val local = draft.copy(id = localId(), authorPubkey = pubkey, published = false)
        highlightDao.upsert(listOf(local.toEntity()))

        return when (local.visibility) {
            HighlightVisibility.Public -> publishPublic(local)
            HighlightVisibility.Private -> publishPrivate(local)
        }
    }

    /**
     * Removes a highlight everywhere we can.
     *
     * A published highlight is already out on relays, so local deletion is not
     * enough: NIP-09 asks the relays to drop it. They are free to refuse, which
     * is why the local row goes regardless of what the network does.
     */
    suspend fun delete(highlight: Highlight) {
        highlightDao.delete(highlight.id)

        if (highlight.visibility == HighlightVisibility.Private) {
            republishPrivateSet()
            return
        }
        if (!highlight.published) return
        requestDeletion(highlight)
    }

    private suspend fun requestDeletion(highlight: Highlight) {
        val signer = signerManager.current.value
        if (!signer.canSign) return

        val unsigned = UnsignedEvent(
            kind = Kinds.DELETION,
            tags = listOf(
                listOf("e", highlight.id),
                listOf("k", Kinds.HIGHLIGHT.toString()),
            ),
            // No reason string: it would be a plaintext "this npub uses
            // Athena" on a public event, and NIP-09 does not require one.
            content = "",
        )
        signer.sign(unsigned)
            .onSuccess(relayPool::publish)
            .onFailure { Log.w(TAG, "deletion request not signed: ${it.message}") }
    }

    /** Flips a highlight between the public feed and the encrypted private set. */
    suspend fun setVisibility(highlight: Highlight, visibility: HighlightVisibility): Highlight {
        if (highlight.visibility == visibility) return highlight

        val updated = highlight.copy(visibility = visibility, published = false)
        highlightDao.upsert(listOf(updated.toEntity()))

        return when (visibility) {
            // Going private means the public event must be retracted, not just hidden.
            HighlightVisibility.Private -> {
                if (highlight.published) requestDeletion(highlight)
                publishPrivate(updated)
            }

            HighlightVisibility.Public -> publishPublic(updated).also { republishPrivateSet() }
        }
    }

    /** Pulls the user's highlights back down, e.g. on a new device. */
    suspend fun syncFrom(pubkey: String) {
        val events = relayPool.fetch(listOf(Filter.highlightsOf(pubkey)))
        val highlights = events.mapNotNull(EventMappers::toHighlight)
        if (highlights.isNotEmpty()) highlightDao.upsert(highlights.map { it.toEntity() })
        syncPrivateSet(pubkey)
    }

    /** Everyone's highlights on a book — the "most highlighted passages" overlay. */
    suspend fun communityHighlights(coordinate: Coordinate): List<Highlight> =
        relayPool.fetch(listOf(Filter.highlightsOn(coordinate))).mapNotNull(EventMappers::toHighlight)

    private suspend fun publishPublic(highlight: Highlight): Highlight {
        val unsigned = EventMappers.toUnsignedEvent(highlight, highlight.bookCoordinate.pubkey)
        val signed = signerManager.current.value.sign(unsigned).getOrElse { error ->
            Log.w(TAG, "could not sign highlight: ${error.message}")
            return highlight
        }
        relayPool.publish(signed)

        // The event id is the real identity; swap the local placeholder for it.
        val published = highlight.copy(id = signed.id, published = true)
        highlightDao.delete(highlight.id)
        highlightDao.upsert(listOf(published.toEntity()))
        return published
    }

    private suspend fun publishPrivate(highlight: Highlight): Highlight {
        val stored = highlight.copy(published = republishPrivateSet())
        highlightDao.upsert(listOf(stored.toEntity()))
        return stored
    }

    /**
     * Rewrites the whole private set: it is one replaceable slot, so adding or
     * removing an entry means republishing the list in full.
     */
    private suspend fun republishPrivateSet(): Boolean {
        val pubkey = signerManager.current.value.pubkeyHex ?: return false
        val secret = encryptedSync.secret() ?: return false

        val entries = highlightDao.byVisibility(pubkey, HighlightVisibility.Private.name)
            .mapNotNull { it.toDomain() }
            .map { it.toPrivateRecord() }

        val payload = json.encodeToString(ListSerializer(PrivateHighlight.serializer()), entries)
        return encryptedSync.put(BlindedPath.privateHighlights(secret), payload)
    }

    /** Decrypts the private set and merges it into the local rows. */
    private suspend fun syncPrivateSet(pubkey: String) {
        val secret = encryptedSync.secret() ?: return
        val plaintext = encryptedSync.get(BlindedPath.privateHighlights(secret)) ?: return

        val records = runCatching {
            json.decodeFromString(ListSerializer(PrivateHighlight.serializer()), plaintext)
        }.getOrElse { error ->
            Log.w(TAG, "private set is not in a shape we understand: ${error.message}")
            return
        }

        val rows = records.mapNotNull { it.toHighlight(pubkey) }
        if (rows.isNotEmpty()) highlightDao.upsert(rows.map { it.toEntity() })
    }

    private fun Highlight.toPrivateRecord() = PrivateHighlight(
        book = bookCoordinate.asString(),
        section = sectionCoordinate?.asString(),
        text = text,
        comment = comment,
        context = context,
        start = startOffset,
        end = endOffset,
        color = color.name,
        createdAt = createdAt,
    )

    private fun PrivateHighlight.toHighlight(pubkey: String): Highlight? {
        val coordinate = Coordinate.parse(book) ?: return null
        return Highlight(
            // Deterministic, so re-syncing the same set does not duplicate rows.
            id = "private:${coordinate.asString()}:$start:$end",
            bookCoordinate = coordinate,
            sectionCoordinate = section?.let(Coordinate::parse),
            text = text,
            comment = comment,
            context = context,
            startOffset = start,
            endOffset = end,
            color = HighlightColor.entries.firstOrNull { it.name == color } ?: HighlightColor.Yellow,
            visibility = HighlightVisibility.Private,
            authorPubkey = pubkey,
            createdAt = if (createdAt > 0) createdAt else nowSeconds(),
            published = true,
        )
    }

    /**
     * A placeholder id for a highlight that has not been signed yet. It is
     * replaced by the real event id as soon as the signer answers.
     */
    private fun localId(): String = "local:" + nowMillis() + ":" + Random.nextInt(Int.MAX_VALUE)

    private companion object {
        const val TAG = "HighlightRepository"
    }
}
