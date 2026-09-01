package com.athena.reader.nostr.crypto

/**
 * ChaCha20 stream cipher, RFC 8439.
 *
 * NIP-44 v2 uses the unauthenticated stream (the authentication is a separate
 * HMAC over the ciphertext), which `javax.crypto` exposes only from Android
 * API 28 — below our minSdk. Sixty lines of well-specified arithmetic is a
 * better trade than either raising minSdk or bundling a crypto provider.
 */
object ChaCha20 {

    private const val ROUNDS = 20

    /** The cipher is symmetric: the same call encrypts and decrypts. */
    fun apply(key: ByteArray, nonce: ByteArray, data: ByteArray, counter: Int = 0): ByteArray {
        require(key.size == 32) { "ChaCha20 key must be 32 bytes, was ${key.size}" }
        require(nonce.size == 12) { "ChaCha20 nonce must be 12 bytes, was ${nonce.size}" }

        val output = ByteArray(data.size)
        val block = IntArray(16)
        val keyStream = ByteArray(64)

        var offset = 0
        var blockCounter = counter
        while (offset < data.size) {
            coreBlock(key, nonce, blockCounter, block)
            serialize(block, keyStream)

            val take = minOf(64, data.size - offset)
            for (i in 0 until take) {
                output[offset + i] = (data[offset + i].toInt() xor keyStream[i].toInt()).toByte()
            }
            offset += take
            blockCounter++
        }
        return output
    }

    /** One 64-byte block: build the state, run the rounds, add the original in. */
    private fun coreBlock(key: ByteArray, nonce: ByteArray, counter: Int, out: IntArray) {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = key.littleEndianInt(i * 4)
        state[12] = counter
        for (i in 0 until 3) state[13 + i] = nonce.littleEndianInt(i * 4)

        state.copyInto(out)
        repeat(ROUNDS / 2) {
            quarterRound(out, 0, 4, 8, 12)
            quarterRound(out, 1, 5, 9, 13)
            quarterRound(out, 2, 6, 10, 14)
            quarterRound(out, 3, 7, 11, 15)
            quarterRound(out, 0, 5, 10, 15)
            quarterRound(out, 1, 6, 11, 12)
            quarterRound(out, 2, 7, 8, 13)
            quarterRound(out, 3, 4, 9, 14)
        }
        for (i in 0 until 16) out[i] += state[i]
    }

    private fun quarterRound(state: IntArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] += state[b]; state[d] = (state[d] xor state[a]) rotateLeft 16
        state[c] += state[d]; state[b] = (state[b] xor state[c]) rotateLeft 12
        state[a] += state[b]; state[d] = (state[d] xor state[a]) rotateLeft 8
        state[c] += state[d]; state[b] = (state[b] xor state[c]) rotateLeft 7
    }

    private infix fun Int.rotateLeft(bits: Int): Int = (this shl bits) or (this ushr (32 - bits))

    private fun ByteArray.littleEndianInt(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun serialize(block: IntArray, out: ByteArray) {
        for (i in 0 until 16) {
            val value = block[i]
            out[i * 4] = value.toByte()
            out[i * 4 + 1] = (value ushr 8).toByte()
            out[i * 4 + 2] = (value ushr 16).toByte()
            out[i * 4 + 3] = (value ushr 24).toByte()
        }
    }
}
