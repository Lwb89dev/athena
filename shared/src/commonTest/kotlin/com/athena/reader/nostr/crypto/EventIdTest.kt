package com.athena.reader.nostr.crypto

import com.athena.reader.nostr.model.NostrEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The event id is the one thing that must be byte-exact: get the canonical
 * serialisation wrong and every event we publish is silently rejected by relays.
 * These vectors are real events, so the expected id is not something we chose.
 */
class EventIdTest {

    @Test
    fun `computes the id of a real event`() {
        val event = NostrEvent(
            id = "4376c65d2f232afbe9b882a35baa4f6fe8667c4e684749af565f981833ed6a65",
            pubkey = "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93",
            createdAt = 1_673_347_337,
            kind = 1,
            tags = listOf(
                listOf("e", "3da979448d9ba263864c4d6f14984c423a3838364ec255f03c7904b1ae77f206"),
                listOf("p", "bf2376e17ba4ec269d10fcc996a4746b451152be9031fa48e74553dde5526bce"),
            ),
            content = "Walled gardens became prisons, and nostr is the first step towards " +
                "tearing down the prison walls.",
            sig = "",
        )

        assertEquals(event.id, EventId.compute(event))
    }

    @Test
    fun `escapes exactly the six sequences NIP-01 allows`() {
        val json = EventId.canonicalJson(
            pubkey = "ab".repeat(32),
            createdAt = 1,
            kind = 1,
            tags = listOf(listOf("t", "a\"b"), listOf("r", "x\\y")),
            content = "line1\nline2\ttab",
        )

        assertTrue(json.endsWith("""[["t","a\"b"],["r","x\\y"]],"line1\nline2\ttab"]"""))
    }

    @Test
    fun `leaves non-ascii alone rather than escaping it`() {
        // A \uXXXX escape here would change the hash and break every accented title.
        val json = EventId.canonicalJson("ab".repeat(32), 1, 1, emptyList(), "perché è così")
        assertTrue(json.contains("perché è così"))
    }

    @Test
    fun `hex round-trips`() {
        val hex = "6e468422dfb74a5738702a8823b9b28168abab8655faacb6853cd0ee15deee93"
        assertEquals(hex, hex.hexToBytes().toHex())
    }
}
