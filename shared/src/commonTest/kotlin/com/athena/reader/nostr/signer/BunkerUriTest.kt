package com.athena.reader.nostr.signer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BunkerUriTest {

    private val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    @Test
    fun `parses a bunker uri with several relays`() {
        val parsed = BunkerUri.parse(
            "bunker://$pubkey?relay=wss%3A%2F%2Frelay.one&relay=wss://relay.two&secret=hunter2",
        )

        assertEquals(pubkey, parsed?.remoteSignerPubkey)
        assertEquals(listOf("wss://relay.one", "wss://relay.two"), parsed?.relays)
        assertEquals("hunter2", parsed?.secret)
    }

    @Test
    fun `rejects a uri with no relay to reach the signer on`() {
        assertNull(BunkerUri.parse("bunker://$pubkey"))
    }

    @Test
    fun `rejects a truncated pubkey`() {
        assertNull(BunkerUri.parse("bunker://abc?relay=wss://relay.one"))
    }
}
