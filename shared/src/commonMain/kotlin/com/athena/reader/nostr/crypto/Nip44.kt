package com.athena.reader.nostr.crypto

import com.athena.reader.platform.randomBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.floor
import kotlin.math.log2

/**
 * NIP-44 v2 encryption.
 *
 * Payload layout, base64 encoded:
 *
 *     version(1) || nonce(32) || ciphertext(…) || mac(32)
 *
 * The padding scheme is not cosmetic: it rounds plaintext up to a power-of-two
 * bucket so that a relay operator watching ciphertext sizes learns much less
 * about what was written. That is why short and medium notes are indistinguishable.
 */
@OptIn(ExperimentalEncodingApi::class)
object Nip44 {

    private const val VERSION: Byte = 2
    private const val MIN_PLAINTEXT = 1
    private const val MAX_PLAINTEXT = 65_535
    private val SALT = "nip44-v2".encodeToByteArray()

    /**
     * The long-lived secret for a pair of keys. Deriving it is the expensive
     * part (an EC multiplication), so callers that encrypt repeatedly to the
     * same peer should hold on to the result.
     */
    fun conversationKey(privateKey: ByteArray, peerPublicKeyXOnly: ByteArray): ByteArray =
        Hmac.hkdfExtract(salt = SALT, inputKeyMaterial = ecdhSharedX(privateKey, peerPublicKeyXOnly))

    fun encrypt(plaintext: String, conversationKey: ByteArray): String =
        encrypt(plaintext, conversationKey, randomBytes(32))

    /** [nonce] is a parameter only so the spec's test vectors can be replayed. */
    fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray): String {
        val bytes = plaintext.encodeToByteArray()
        require(bytes.size in MIN_PLAINTEXT..MAX_PLAINTEXT) {
            "NIP-44 plaintext must be 1..$MAX_PLAINTEXT bytes, was ${bytes.size}"
        }
        require(nonce.size == 32) { "NIP-44 nonce is 32 bytes" }

        val keys = messageKeys(conversationKey, nonce)
        val ciphertext = ChaCha20.apply(keys.chachaKey, keys.chachaNonce, pad(bytes))
        val mac = Hmac.sha256(keys.hmacKey, nonce + ciphertext)

        return Base64.encode(byteArrayOf(VERSION) + nonce + ciphertext + mac)
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        require(payload.isNotEmpty()) { "empty NIP-44 payload" }
        require(payload[0] != '#') { "unsupported NIP-44 version" }

        val decoded = runCatching { Base64.decode(payload) }.getOrElse {
            throw IllegalArgumentException("NIP-44 payload is not valid base64")
        }
        require(decoded.size >= 99) { "NIP-44 payload is too short: ${decoded.size} bytes" }
        require(decoded[0] == VERSION) { "unsupported NIP-44 version ${decoded[0]}" }

        val nonce = decoded.copyOfRange(1, 33)
        val ciphertext = decoded.copyOfRange(33, decoded.size - 32)
        val mac = decoded.copyOfRange(decoded.size - 32, decoded.size)

        val keys = messageKeys(conversationKey, nonce)
        // Authenticate before decrypting: never act on bytes we have not verified.
        require(Hmac.sha256(keys.hmacKey, nonce + ciphertext).constantTimeEquals(mac)) {
            "NIP-44 authentication failed"
        }

        return unpad(ChaCha20.apply(keys.chachaKey, keys.chachaNonce, ciphertext))
    }

    private class MessageKeys(
        val chachaKey: ByteArray,
        val chachaNonce: ByteArray,
        val hmacKey: ByteArray,
    )

    private fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        require(conversationKey.size == 32) { "conversation key is 32 bytes" }
        val expanded = Hmac.hkdfExpand(conversationKey, nonce, 76)
        return MessageKeys(
            chachaKey = expanded.copyOfRange(0, 32),
            chachaNonce = expanded.copyOfRange(32, 44),
            hmacKey = expanded.copyOfRange(44, 76),
        )
    }

    /** `u16_be(length) || plaintext || zeros`, total length from [paddedLength]. */
    private fun pad(plaintext: ByteArray): ByteArray {
        val total = paddedLength(plaintext.size)
        val padded = ByteArray(2 + total)
        padded[0] = (plaintext.size ushr 8).toByte()
        padded[1] = plaintext.size.toByte()
        plaintext.copyInto(padded, 2)
        return padded
    }

    private fun unpad(padded: ByteArray): String {
        require(padded.size >= 2) { "NIP-44 plaintext block is truncated" }
        val length = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)

        require(length in MIN_PLAINTEXT..MAX_PLAINTEXT) { "invalid NIP-44 plaintext length" }
        require(padded.size == 2 + paddedLength(length)) { "invalid NIP-44 padding" }

        return padded.decodeToString(2, 2 + length)
    }

    /**
     * Power-of-two bucketing, so ciphertext size leaks as little as possible.
     * Internal rather than private so the spec's padding table can be asserted
     * against the real function instead of a copy of it.
     */
    internal fun paddedLength(unpaddedLength: Int): Int {
        if (unpaddedLength <= 32) return 32
        val nextPower = 1 shl (floor(log2((unpaddedLength - 1).toDouble())).toInt() + 1)
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((unpaddedLength - 1) / chunk + 1)
    }
}
