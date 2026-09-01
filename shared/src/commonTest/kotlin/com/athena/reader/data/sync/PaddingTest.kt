package com.athena.reader.data.sync

import com.athena.reader.nostr.crypto.KeyPair
import com.athena.reader.nostr.crypto.Nip44
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Size is a side channel: without coarse padding, ciphertext length tracks how
 * many books a user has marked and how that number grows over time. NIP-44 pads
 * too, but in buckets far too fine to hide "one favourite" from "fifty".
 *
 * These are the assertions that fail if someone ever "optimises" the padding
 * away as wasted bytes.
 */
class PaddingTest {

    @Test
    fun `pads to a coarse bucket`() {
        assertEquals(4_096, padToBucket("a").length)
        assertEquals(4_096, padToBucket("a".repeat(4_000)).length)
        assertEquals(8_192, padToBucket("a".repeat(4_097)).length)
    }

    @Test
    fun `a growing shelf keeps one ciphertext length`() {
        val key = Nip44.conversationKey(KeyPair.generate().privateKey, KeyPair.generate().publicKey)

        val oneBook = Nip44.encrypt(padToBucket("""["30040:aa:one"]"""), key)
        val fiftyBooks = Nip44.encrypt(
            padToBucket(List(50) { "\"30040:aa:book$it\"" }.joinToString(",", "[", "]")),
            key,
        )

        assertEquals(
            oneBook.length,
            fiftyBooks.length,
            "1 and 50 favourites must be indistinguishable by ciphertext length",
        )
    }

    @Test
    fun `padding is whitespace so the JSON survives the round trip`() {
        val payload = """{"section":3,"offset":120}"""
        val padded = padToBucket(payload)

        assertTrue(padded.startsWith(payload))
        assertEquals(payload, padded.trimEnd())
    }

    @Test
    fun `multi-byte characters are measured in bytes not characters`() {
        // A char-based count would under-pad and leak through the byte length.
        val payload = "è".repeat(100)
        assertTrue(padToBucket(payload).encodeToByteArray().size % 4_096 == 0)
    }
}
