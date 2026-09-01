package com.athena.reader.data.sync

import com.athena.reader.data.session.SessionStore
import com.athena.reader.data.session.SyncMode
import com.athena.reader.nostr.crypto.BlindedPath
import com.athena.reader.nostr.crypto.Pbkdf2
import com.athena.reader.nostr.crypto.hexToBytes
import com.athena.reader.nostr.crypto.toHex
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Log
import com.athena.reader.platform.ioDispatcher
import com.athena.reader.platform.randomBytes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SecretEnvelope(val v: Int = 1, val blind: String)

/**
 * The per-user blinding secret every private slot address is derived from.
 *
 * Two devices must agree on it, but neither ever sees the user's private key —
 * Amber and the bunker hold it. There is no way around that being either
 * *published somewhere* or *supplied by the user*, so the app offers both and
 * makes the trade-off explicit rather than choosing silently:
 *
 *  - [SyncMode.Passphrase] — the secret is `PBKDF2(passphrase, salt = pubkey)`.
 *    **Nothing is published.** A relay cannot tell this npub uses Athena at
 *    all, because there is no slot at a computable address to probe for. The
 *    cost is that the user types a passphrase once per device.
 *
 *  - [SyncMode.RelayBootstrap] — a random secret is sealed to the user's own
 *    key and published in one event at `sha256("athena-v1:" + pubkey)`.
 *    Convenient, but that address is computable by anyone: a relay can answer
 *    "does this npub use Athena?" and someone could act on the answer.
 *
 *  - [SyncMode.Off] — no private sync. Private highlights and favourites stay
 *    on this device and nothing about them reaches a relay. This is the default,
 *    because turning on a feature that publishes a discoverable marker is not a
 *    decision an app should make for someone.
 */
class SyncSecret(
    private val relayPool: RelayPool,
    private val signerManager: SignerManager,
    private val sessionStore: SessionStore,
    private val json: Json,
) {
    private val lock = Mutex()
    private var cached: ByteArray? = null
    private var cachedFor: String? = null

    /**
     * The secret for the logged-in user, or null when private sync is off, no
     * one is logged in, or the passphrase has not been entered on this device.
     * Callers then keep data local rather than publishing to a guessable place.
     */
    suspend fun get(): ByteArray? = lock.withLock {
        val pubkey = signerManager.current.value.pubkeyHex ?: return null
        cached?.takeIf { cachedFor == pubkey }?.let { return it }

        val secret = when (sessionStore.session.first().syncMode) {
            SyncMode.Off -> null
            SyncMode.Passphrase -> storedSecret()
            SyncMode.RelayBootstrap -> recover(pubkey) ?: create(pubkey)
        } ?: return null

        cached = secret
        cachedFor = pubkey
        secret
    }

    /**
     * Derives and caches the secret from [passphrase]. Returns false only when
     * nobody is logged in — a "wrong" passphrase is indistinguishable from a
     * right one until a slot fails to decrypt, which is inherent to the scheme.
     */
    suspend fun unlockWithPassphrase(passphrase: String): Boolean = lock.withLock {
        val pubkey = signerManager.current.value.pubkeyHex ?: return false

        val secret = derive(passphrase, pubkey)
        cached = secret
        cachedFor = pubkey
        // The derived secret is stored, never the passphrase: if this device's
        // storage is read, the damage stays scoped to Athena's slots instead
        // of handing over a phrase the user may have reused elsewhere.
        sessionStore.saveSyncSecret(secret.toHex(), SyncMode.Passphrase)
        true
    }

    suspend fun setMode(mode: SyncMode) = lock.withLock {
        cached = null
        cachedFor = null
        sessionStore.saveSyncMode(mode)
    }

    /** True when this device can already derive slot addresses. */
    suspend fun isUnlocked(): Boolean = get() != null

    /** Forgets the cached secret. Called when the identity changes. */
    suspend fun clear() = lock.withLock {
        cached = null
        cachedFor = null
    }

    /** Set by [unlockWithPassphrase]; absent until the user unlocks this device. */
    private suspend fun storedSecret(): ByteArray? {
        val hex = sessionStore.session.first().syncSecretHex ?: return null
        return runCatching { hex.hexToBytes() }
            .getOrNull()
            ?.takeIf { it.size == BlindedPath.SECRET_SIZE }
    }

    /**
     * Salted with the pubkey so that two users who pick the same passphrase do
     * not land on the same slots — otherwise a relay could group them.
     */
    private suspend fun derive(passphrase: String, pubkey: String): ByteArray =
        withContext(ioDispatcher) {
            Pbkdf2.derive(
                passphrase = passphrase,
                salt = pubkey.hexToBytes(),
                length = BlindedPath.SECRET_SIZE,
            )
        }

    private suspend fun recover(pubkey: String): ByteArray? {
        val filter = Filter(
            authors = listOf(pubkey),
            kinds = listOf(Kinds.APP_DATA),
            tags = mapOf("d" to listOf(BlindedPath.bootstrapSlot(pubkey))),
            limit = 1,
        )
        val event = relayPool.fetch(listOf(filter)).maxByOrNull { it.createdAt } ?: return null

        val plaintext = signerManager.current.value.decryptFromSelf(event.content).getOrElse {
            Log.w(TAG, "the sync secret exists but could not be opened: ${it.message}")
            return null
        }
        val envelope = runCatching {
            json.decodeFromString(SecretEnvelope.serializer(), plaintext.trim())
        }.getOrNull() ?: return null

        return runCatching { envelope.blind.hexToBytes() }
            .getOrNull()
            ?.takeIf { it.size == BlindedPath.SECRET_SIZE }
    }

    private suspend fun create(pubkey: String): ByteArray? {
        val signer = signerManager.current.value
        val secret = randomBytes(BlindedPath.SECRET_SIZE)

        val payload = json.encodeToString(
            SecretEnvelope.serializer(),
            SecretEnvelope(blind = secret.toHex()),
        )
        val ciphertext = signer.encryptToSelf(payload).getOrElse {
            Log.w(TAG, "could not seal a new sync secret: ${it.message}")
            return null
        }

        val unsigned = UnsignedEvent(
            kind = Kinds.APP_DATA,
            tags = listOf(listOf("d", BlindedPath.bootstrapSlot(pubkey))),
            content = ciphertext,
        )
        val signed = signer.sign(unsigned).getOrElse {
            Log.w(TAG, "could not sign the sync secret: ${it.message}")
            return null
        }
        relayPool.publish(signed)
        Log.d(TAG, "published a fresh sync secret for this identity")
        return secret
    }

    private companion object {
        const val TAG = "SyncSecret"
    }
}
