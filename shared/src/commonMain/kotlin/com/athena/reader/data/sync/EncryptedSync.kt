package com.athena.reader.data.sync

import com.athena.reader.data.local.SyncStateDao
import com.athena.reader.data.local.SyncStateEntity
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Log
import com.athena.reader.platform.nowSeconds
import kotlin.math.max
import kotlin.math.min

/**
 * Every private thing the app syncs goes through here, and nothing else writes
 * to a relay unencrypted.
 *
 * A NIP-78 event leaks on four channels even when its content is sealed, and
 * each is closed deliberately:
 *
 *  - **address** — the `d` tag is indexed, so a readable one tells a relay what
 *    the slot is about. Callers pass a [BlindedPath] tag: opaque, unlinkable.
 *  - **content** — NIP-44 self-encryption, ciphertext only. Never a tag, never
 *    a field, never a plaintext fallback.
 *  - **size** — ciphertext length tracks payload growth. NIP-44's own padding
 *    is too fine-grained to hide "how many books" or "how many highlights", so
 *    the plaintext is padded to a coarse bucket first.
 *  - **time** — `created_at` at second resolution broadcasts exactly when the
 *    user turned a page. It is rounded down to the UTC hour, with a persisted
 *    bump so replaceable-event ordering still works.
 *
 * Reads carry the fourth defence: a high-water mark per slot, so a relay cannot
 * replay an older, validly-signed snapshot to resurrect deleted data.
 */
class EncryptedSync(
    private val relayPool: RelayPool,
    private val signerManager: SignerManager,
    private val syncSecret: SyncSecret,
    private val syncStateDao: SyncStateDao,
) {
    /** Available only when logged in and the blinding secret could be resolved. */
    suspend fun secret(): ByteArray? = syncSecret.get()

    /**
     * Publishes [plaintext] into [slot]. Returns false when nothing went out —
     * the caller keeps its local copy and can retry later.
     */
    suspend fun put(slot: String, plaintext: String): Boolean {
        val signer = signerManager.current.value
        if (!signer.canSign) return false

        val ciphertext = signer.encryptToSelf(padToBucket(plaintext)).getOrElse { error ->
            Log.w(TAG, "slot not published, encryption refused: ${error.message}")
            return false
        }

        val createdAt = nextCreatedAt(slot)
        val unsigned = UnsignedEvent(
            createdAt = createdAt,
            kind = Kinds.APP_DATA,
            tags = listOf(listOf("d", slot)),
            content = ciphertext,
        )
        val signed = signer.sign(unsigned).getOrElse { error ->
            Log.w(TAG, "slot not published, signing refused: ${error.message}")
            return false
        }

        relayPool.publish(signed)
        remember(slot, seenAt = createdAt, publishedAt = createdAt)
        return true
    }

    /**
     * Reads [slot] back. Returns null when it is empty, undecryptable, or older
     * than something this device has already seen.
     */
    suspend fun get(slot: String): String? {
        val signer = signerManager.current.value
        val pubkey = signer.pubkeyHex ?: return null

        val filter = Filter(
            authors = listOf(pubkey),
            kinds = listOf(Kinds.APP_DATA),
            tags = mapOf("d" to listOf(slot)),
            limit = 1,
        )
        val event = relayPool.fetch(listOf(filter)).maxByOrNull { it.createdAt } ?: return null

        // Anti-rollback: a validly-signed but stale snapshot is still an attack.
        val state = syncStateDao.get(slot)
        if (state != null && event.createdAt < state.lastSeenAt) {
            Log.w(TAG, "ignoring a rolled-back snapshot for a sync slot")
            return null
        }

        val plaintext = signer.decryptFromSelf(event.content).getOrElse { error ->
            Log.d(TAG, "slot not decryptable: ${error.message}")
            return null
        }
        remember(slot, seenAt = event.createdAt, publishedAt = state?.lastPublishedAt ?: 0)
        return plaintext.trimEnd()
    }

    suspend fun forgetAll() = syncStateDao.clear()

    private suspend fun remember(slot: String, seenAt: Long, publishedAt: Long) {
        val previous = syncStateDao.get(slot)
        syncStateDao.upsert(
            SyncStateEntity(
                slot = slot,
                lastSeenAt = max(seenAt, previous?.lastSeenAt ?: 0),
                lastPublishedAt = max(publishedAt, previous?.lastPublishedAt ?: 0),
            ),
        )
    }

    /**
     * Start of the current UTC hour, bumped past whatever we last published so
     * that two edits inside one hour still replace in the right order.
     */
    private suspend fun nextCreatedAt(slot: String): Long {
        val now = nowSeconds()
        val hourStart = now - now % SECONDS_PER_HOUR
        val last = syncStateDao.get(slot)?.lastPublishedAt ?: 0
        return max(hourStart, last + 1)
    }

    private companion object {
        const val TAG = "EncryptedSync"
        const val SECONDS_PER_HOUR = 3_600L
    }
}

/**
 * Pads with spaces to a coarse bucket, so ciphertext length stops tracking how
 * much the user has stored. Spaces because every payload here is JSON, where
 * trailing whitespace is insignificant — the pad survives the round trip
 * without needing a length field of its own.
 *
 * 4 KiB buckets: coarse enough that adding a book or a highlight almost never
 * moves the ciphertext to a new size, which is the entire point. NIP-44 pads
 * too, but in buckets far too fine to hide "one favourite" from "fifty".
 *
 * Top-level because it depends on nothing else, and is worth testing on its own.
 */
internal fun padToBucket(plaintext: String, bucket: Int = PAD_BUCKET): String {
    val length = plaintext.encodeToByteArray().size
    require(length <= MAX_PLAINTEXT) { "payload exceeds NIP-44's limit: $length bytes" }
    if (length == MAX_PLAINTEXT) return plaintext

    val target = min(((length + bucket - 1) / bucket) * bucket, MAX_PLAINTEXT)
    return plaintext + " ".repeat(target - length)
}

internal const val PAD_BUCKET = 4_096
private const val MAX_PLAINTEXT = 65_535
