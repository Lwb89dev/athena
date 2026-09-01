package com.athena.reader.nostr.crypto

import com.athena.reader.nostr.model.NostrEvent
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.platform.randomBytes
import fr.acinq.secp256k1.Secp256k1

/**
 * A secp256k1 keypair.
 *
 * The app holds one of these in exactly one situation: the ephemeral client key
 * a NIP-46 session needs to talk to a remote signer. The user's own key is never
 * here — that is the whole point of NIP-55 and NIP-46.
 */
class KeyPair(val privateKey: ByteArray) {

    init {
        require(privateKey.size == 32) { "a private key is 32 bytes, was ${privateKey.size}" }
        require(Secp256k1.secKeyVerify(privateKey)) { "not a valid secp256k1 private key" }
    }

    /** x-only public key, the 32-byte form nostr uses everywhere. */
    val publicKey: ByteArray by lazy {
        // pubkeyCreate returns the 65-byte uncompressed point; nostr wants x only.
        Secp256k1.pubkeyCreate(privateKey).copyOfRange(1, 33)
    }

    val publicKeyHex: String get() = publicKey.toHex()

    fun signSchnorr(message: ByteArray): ByteArray {
        require(message.size == 32) { "BIP-340 signs a 32-byte digest" }
        return Secp256k1.signSchnorr(message, privateKey, randomBytes(32))
    }

    companion object {
        fun generate(): KeyPair {
            while (true) {
                val candidate = randomBytes(32)
                if (Secp256k1.secKeyVerify(candidate)) return KeyPair(candidate)
            }
        }

        fun fromHex(hex: String) = KeyPair(hex.hexToBytes())
    }
}

/**
 * Signs an event with this keypair.
 *
 * Only ever used for the NIP-46 transport envelope (kind 24133), which is signed
 * by the *session* key. The user's own events are never signed here — they go
 * out to Amber or the bunker.
 */
fun KeyPair.signEvent(unsigned: UnsignedEvent): NostrEvent {
    val pubkey = publicKeyHex
    val id = EventId.compute(pubkey, unsigned)
    return NostrEvent(
        id = id,
        pubkey = pubkey,
        createdAt = unsigned.createdAt,
        kind = unsigned.kind,
        tags = unsigned.tags,
        content = unsigned.content,
        sig = signSchnorr(id.hexToBytes()).toHex(),
    )
}

/**
 * The ECDH shared secret NIP-44 is built on: the *x coordinate only*, unhashed.
 *
 * `Secp256k1.ecdh` is not usable here — it returns SHA-256 of the compressed
 * point, which is the standard construction but not the one NIP-44 specifies.
 */
fun ecdhSharedX(privateKey: ByteArray, peerPublicKeyXOnly: ByteArray): ByteArray {
    require(peerPublicKeyXOnly.size == 32) { "expected an x-only public key" }

    // An x-only key is lifted to a point by assuming the even-y solution, which
    // is exactly what BIP-340 prescribes.
    val compressed = byteArrayOf(0x02) + peerPublicKeyXOnly
    val point = Secp256k1.pubkeyParse(compressed)
    val shared = Secp256k1.pubKeyTweakMul(point, privateKey)
    return shared.copyOfRange(1, 33)
}
