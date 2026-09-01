package com.athena.reader.nostr.signer

import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.nostr.model.UnsignedEvent

/**
 * Everything the app can ask a key holder to do. Athena never sees a private
 * key: implementations delegate to Amber (NIP-55) or a remote bunker (NIP-46).
 */
interface NostrSigner {

    /** Hex public key of the logged-in identity, or null when browsing anonymously. */
    val pubkeyHex: String?

    val canSign: Boolean get() = pubkeyHex != null

    suspend fun requestPublicKey(): Result<String>

    suspend fun sign(unsigned: UnsignedEvent): Result<NostrEvent>

    /** NIP-44 v2 encryption to [peerPubkeyHex]; self-encryption uses our own key. */
    suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): Result<String>

    suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): Result<String>

    /** Encrypts to ourselves — how NIP-51 private list entries are stored. */
    suspend fun encryptToSelf(plaintext: String): Result<String> {
        val self = pubkeyHex ?: return Result.failure(NotLoggedIn)
        return nip44Encrypt(plaintext, self)
    }

    suspend fun decryptFromSelf(ciphertext: String): Result<String> {
        val self = pubkeyHex ?: return Result.failure(NotLoggedIn)
        return nip44Decrypt(ciphertext, self)
    }

    object NotLoggedIn : IllegalStateException("No signer is connected")
}

/** Read-only browsing: the library is public, so this is the default state. */
object AnonymousSigner : NostrSigner {
    override val pubkeyHex: String? = null
    override suspend fun requestPublicKey() = Result.failure<String>(NostrSigner.NotLoggedIn)
    override suspend fun sign(unsigned: UnsignedEvent) = Result.failure<NostrEvent>(NostrSigner.NotLoggedIn)
    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String) =
        Result.failure<String>(NostrSigner.NotLoggedIn)
    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String) =
        Result.failure<String>(NostrSigner.NotLoggedIn)
}

/** The user closed the signer, or no signer answered. */
object SignerDismissed : IllegalStateException("The signer was dismissed or is not installed")

/** The signer answered, but not with something we can use. */
class SignerProtocolError(message: String) : IllegalStateException(message)
