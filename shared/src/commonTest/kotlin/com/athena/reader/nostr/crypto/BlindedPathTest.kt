package com.athena.reader.nostr.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * These assert the *privacy properties* the design claims, not merely that the
 * function runs. Each test corresponds to one thing a malicious relay must not
 * be able to do.
 */
class BlindedPathTest {

    private val secret = ByteArray(32) { it.toByte() }
    private val otherSecret = ByteArray(32) { (it + 1).toByte() }
    private val book = "30040:${"ab".repeat(32)}:on-the-nature-of-things"

    @Test
    fun `the same book on the same device always lands in the same slot`() {
        assertEquals(BlindedPath.progress(secret, book), BlindedPath.progress(secret, book))
    }

    @Test
    fun `a slot reveals nothing readable about the book`() {
        val slot = BlindedPath.progress(secret, book)

        assertEquals(64, slot.length)
        assertTrue(slot.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue("on-the-nature-of-things" !in slot)
        assertTrue("30040" !in slot)
    }

    @Test
    fun `two users reading the same book do not share a slot`() {
        // Otherwise a relay could group every reader of a given book together.
        assertNotEquals(
            BlindedPath.progress(secret, book),
            BlindedPath.progress(otherSecret, book),
        )
    }

    @Test
    fun `two books for the same user do not collide`() {
        assertNotEquals(
            BlindedPath.progress(secret, book),
            BlindedPath.progress(secret, "30040:${"ab".repeat(32)}:another-book"),
        )
    }

    @Test
    fun `namespaces do not collide with each other`() {
        val slots = setOf(
            BlindedPath.progress(secret, ""),
            BlindedPath.privateFavorites(secret),
            BlindedPath.privateHighlights(secret),
        )
        assertEquals(3, slots.size, "each namespace must have its own slot")
    }

    @Test
    fun `the slot is not computable from public information alone`() {
        // A bare sha256 of the coordinate would be: the coordinate space is
        // public and enumerable, so this is the property that matters.
        val unkeyed = com.athena.reader.platform.sha256(book.encodeToByteArray()).toHex()
        assertNotEquals(unkeyed, BlindedPath.progress(secret, book))
    }

    @Test
    fun `the bootstrap slot is stable per pubkey and differs between users`() {
        val alice = "ab".repeat(32)
        val bob = "cd".repeat(32)

        assertEquals(BlindedPath.bootstrapSlot(alice), BlindedPath.bootstrapSlot(alice))
        assertNotEquals(BlindedPath.bootstrapSlot(alice), BlindedPath.bootstrapSlot(bob))
    }

    @Test
    fun `rejects a secret of the wrong size rather than silently weakening`() {
        assertFailsWith<IllegalArgumentException> {
            BlindedPath.progress(ByteArray(16), book)
        }
    }
}
