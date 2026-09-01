package com.athena.reader.nostr.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Vectors cross-checked against an independent implementation (Python's
 * `hashlib.pbkdf2_hmac`), not against this code's own output — a KDF that is
 * wrong but self-consistent would derive a perfectly stable secret that simply
 * does not match what any other tool computes, and the user would discover it
 * only when a second device failed to sync.
 */
class Pbkdf2Test {

    @Test
    fun `matches the reference implementation`() {
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            Pbkdf2.derive("password", "salt".encodeToByteArray(), iterations = 1).toHex(),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            Pbkdf2.derive("password", "salt".encodeToByteArray(), iterations = 2).toHex(),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            Pbkdf2.derive("password", "salt".encodeToByteArray(), iterations = 4_096).toHex(),
        )
    }

    @Test
    fun `spans several output blocks correctly`() {
        assertEquals(
            "348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1c635518c7dac47e9",
            Pbkdf2.derive(
                passphrase = "passwordPASSWORDpassword",
                salt = "saltSALTsaltSALTsaltSALTsaltSALTsalt".encodeToByteArray(),
                iterations = 4_096,
                length = 40,
            ).toHex(),
        )
    }

    @Test
    fun `handles a passphrase with accents and emoji`() {
        // Users pick real words. A char-based encoding would diverge here.
        assertEquals(
            "48e7ec8d76c67d3ae719a8a56e84a7a2af9411c86e86b1ba590a76f825efd7bb",
            Pbkdf2.derive(
                passphrase = "perché è così 🔦",
                salt = ByteArray(32) { 0xAB.toByte() },
                iterations = 1_000,
            ).toHex(),
        )
    }

    @Test
    fun `the salt separates two users with the same passphrase`() {
        val alice = Pbkdf2.derive("correct horse", ByteArray(32) { 1 }, iterations = 1_000)
        val bob = Pbkdf2.derive("correct horse", ByteArray(32) { 2 }, iterations = 1_000)

        assertNotEquals(alice.toHex(), bob.toHex())
    }

    @Test
    fun `the default iteration count stays expensive enough to matter`() {
        // Guards against someone "fixing a slow login" by dropping the count:
        // this is the only thing between a weak passphrase and offline guessing.
        assertTrue(
            Pbkdf2.DEFAULT_ITERATIONS >= 600_000,
            "PBKDF2-HMAC-SHA256 below 600k iterations is under the OWASP floor",
        )
    }
}
