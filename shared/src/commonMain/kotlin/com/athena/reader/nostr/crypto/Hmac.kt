package com.athena.reader.nostr.crypto

import com.athena.reader.platform.sha256 as digest

/**
 * HMAC-SHA256 and HKDF (RFC 2104 / RFC 5869), built on the platform's SHA-256.
 *
 * Written here rather than pulled from javax.crypto because NIP-44 needs the
 * same bytes on every target, and Android below API 28 does not ship every
 * primitive the JVM does. The construction is small enough that owning it is
 * cheaper than discovering the gap at runtime.
 */
object Hmac {

    private const val BLOCK_SIZE = 64

    fun sha256(key: ByteArray, message: ByteArray): ByteArray {
        val normalized = normalizeKey(key)

        val inner = ByteArray(BLOCK_SIZE) { i -> (normalized[i].toInt() xor 0x36).toByte() }
        val outer = ByteArray(BLOCK_SIZE) { i -> (normalized[i].toInt() xor 0x5C).toByte() }

        return digest(outer + digest(inner + message))
    }

    private fun normalizeKey(key: ByteArray): ByteArray {
        val shortened = if (key.size > BLOCK_SIZE) digest(key) else key
        return shortened.copyOf(BLOCK_SIZE)
    }

    /** RFC 5869 step 1: turn arbitrary input keying material into a PRK. */
    fun hkdfExtract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray =
        sha256(salt, inputKeyMaterial)

    /** RFC 5869 step 2: stretch the PRK into [length] bytes of output. */
    fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * 32) { "HKDF cannot expand beyond 8160 bytes" }

        val output = ByteArray(length)
        var block = ByteArray(0)
        var written = 0
        var counter = 1

        while (written < length) {
            block = sha256(prk, block + info + byteArrayOf(counter.toByte()))
            val take = minOf(block.size, length - written)
            block.copyInto(output, written, 0, take)
            written += take
            counter++
        }
        return output
    }
}

/** Constant-time comparison: a fast-exit equals here would leak the MAC. */
fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
    if (size != other.size) return false
    var difference = 0
    for (i in indices) difference = difference or (this[i].toInt() xor other[i].toInt())
    return difference == 0
}
