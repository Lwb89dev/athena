package com.athena.reader.nostr.crypto

/**
 * PBKDF2-HMAC-SHA256 (RFC 8018).
 *
 * Used to turn a passphrase the user remembers into the blinding secret, so
 * that two devices can agree on one without ever publishing anything a relay
 * could see. The iteration count is the whole defence here: the passphrase is
 * low-entropy by nature, and the only thing standing between it and an offline
 * guessing attack is how long each guess costs.
 *
 * Same primitive Roadstr uses for its optional sync passphrase, deliberately —
 * one less thing to reason about across the two apps.
 */
object Pbkdf2 {

    /**
     * OWASP's 2023 floor for PBKDF2-HMAC-SHA256. Measured at ~1.4 s on a
     * desktop JVM (see Pbkdf2Test), which is the right order for something the
     * user does once per device, and expensive enough to make bulk guessing
     * against a stolen relay archive impractical.
     */
    const val DEFAULT_ITERATIONS = 600_000

    fun derive(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        length: Int = 32,
    ): ByteArray {
        require(iterations > 0) { "iteration count must be positive" }
        require(length > 0) { "derived key length must be positive" }

        val password = passphrase.encodeToByteArray()
        val output = ByteArray(length)
        val blockCount = (length + HASH_SIZE - 1) / HASH_SIZE

        var written = 0
        for (block in 1..blockCount) {
            val chunk = deriveBlock(password, salt, iterations, block)
            val take = minOf(chunk.size, length - written)
            chunk.copyInto(output, written, 0, take)
            written += take
        }
        return output
    }

    /** F(P, S, c, i) = U1 xor U2 xor … xor Uc, with U1 = PRF(P, S || INT(i)). */
    private fun deriveBlock(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        blockIndex: Int,
    ): ByteArray {
        val indexBytes = byteArrayOf(
            (blockIndex ushr 24).toByte(),
            (blockIndex ushr 16).toByte(),
            (blockIndex ushr 8).toByte(),
            blockIndex.toByte(),
        )

        var u = Hmac.sha256(password, salt + indexBytes)
        val accumulator = u.copyOf()

        for (round in 2..iterations) {
            u = Hmac.sha256(password, u)
            for (i in accumulator.indices) {
                accumulator[i] = (accumulator[i].toInt() xor u[i].toInt()).toByte()
            }
        }
        return accumulator
    }

    private const val HASH_SIZE = 32
}
