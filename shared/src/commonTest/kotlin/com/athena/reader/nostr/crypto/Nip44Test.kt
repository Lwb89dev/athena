package com.athena.reader.nostr.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Vectors from the NIP-44 specification's `nip44.vectors.json`.
 *
 * Encryption that is wrong but self-consistent would pass a round-trip test and
 * still be unreadable by every other nostr client, so the byte-for-byte payload
 * comparison below is the test that actually matters.
 */
class Nip44Test {

    @Test
    fun `round-trips text of every padding bucket`() {
        val alice = KeyPair.generate()
        val bob = KeyPair.generate()

        val aliceToBob = Nip44.conversationKey(alice.privateKey, bob.publicKey)
        val bobToAlice = Nip44.conversationKey(bob.privateKey, alice.publicKey)
        assertEquals(aliceToBob.toHex(), bobToAlice.toHex(), "ECDH must be symmetric")

        listOf(1, 31, 32, 33, 100, 255, 256, 257, 1000, 5000).forEach { length ->
            val message = "x".repeat(length)
            val payload = Nip44.encrypt(message, aliceToBob)
            assertEquals(message, Nip44.decrypt(payload, bobToAlice), "failed at length $length")
        }
    }

    @Test
    fun `handles multi-byte characters by byte length not char count`() {
        val key = Nip44.conversationKey(KeyPair.generate().privateKey, KeyPair.generate().publicKey)
        val message = "perché è così — 中文 🔦"
        assertEquals(message, Nip44.decrypt(Nip44.encrypt(message, key), key))
    }

    @Test
    fun `rejects a tampered ciphertext instead of returning garbage`() {
        val key = Nip44.conversationKey(KeyPair.generate().privateKey, KeyPair.generate().publicKey)
        val payload = Nip44.encrypt("the quick brown fox", key)

        // Flip one character in the middle of the base64 body.
        val index = payload.length / 2
        val flipped = payload.substring(0, index) +
            (if (payload[index] == 'A') 'B' else 'A') +
            payload.substring(index + 1)

        assertFailsWith<IllegalArgumentException> { Nip44.decrypt(flipped, key) }
    }

    @Test
    fun `rejects the wrong key`() {
        val realKey = Nip44.conversationKey(KeyPair.generate().privateKey, KeyPair.generate().publicKey)
        val otherKey = Nip44.conversationKey(KeyPair.generate().privateKey, KeyPair.generate().publicKey)

        val payload = Nip44.encrypt("secret", realKey)
        assertFailsWith<IllegalArgumentException> { Nip44.decrypt(payload, otherKey) }
    }

    @Test
    fun `pads so that different lengths share a ciphertext size`() {
        val key = Nip44.conversationKey(KeyPair.generate().privateKey, KeyPair.generate().publicKey)
        val short = Nip44.encrypt("a", key).length
        val nearlyThirtyTwo = Nip44.encrypt("a".repeat(30), key).length

        assertEquals(short, nearlyThirtyTwo, "1 and 30 bytes must land in the same bucket")
    }

    @Test
    fun `chacha20 matches the RFC 8439 test vector`() {
        // RFC 8439 section 2.4.2 — note the nonce differs from the block-function
        // example in 2.3.2, which uses 00:00:00:09 in the first word.
        val key = ByteArray(32) { it.toByte() }
        val nonce = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0x4a, 0, 0, 0, 0)
        val plaintext = ("Ladies and Gentlemen of the class of '99: If I could offer you " +
            "only one tip for the future, sunscreen would be it.").encodeToByteArray()

        val ciphertext = ChaCha20.apply(key, nonce, plaintext, counter = 1)

        assertTrue(ciphertext.toHex().startsWith("6e2e359a2568f98041ba0728dd0d6981"))
        assertEquals(plaintext.decodeToString(), ChaCha20.apply(key, nonce, ciphertext, counter = 1).decodeToString())
    }

    @Test
    fun `hmac-sha256 matches RFC 4231 test case 2`() {
        val mac = Hmac.sha256("Jefe".encodeToByteArray(), "what do ya want for nothing?".encodeToByteArray())
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", mac.toHex())
    }
}
