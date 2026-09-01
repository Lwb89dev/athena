package com.athena.reader.nostr.crypto

import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Kinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Nip19Test {

    private val pubkey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    @Test
    fun `encodes the npub from the NIP-19 spec`() {
        assertEquals(
            "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6",
            Nip19.encodeNpub(pubkey),
        )
    }

    @Test
    fun `decodes that npub back to hex`() {
        val npub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"
        assertEquals(pubkey, Nip19.decodeNpub(npub))
    }

    @Test
    fun `naddr survives a round trip with relay hints`() {
        val coordinate = Coordinate(Kinds.PUBLICATION_INDEX, pubkey, "on-the-nature-of-things")
        val naddr = Nip19.encodeNaddr(coordinate, listOf("wss://thecitadel.nostr1.com"))

        assertEquals(coordinate, Nip19.decodeNaddr(naddr))
    }

    @Test
    fun `accepts a nostr uri and an app deep link`() {
        val coordinate = Coordinate(Kinds.LONG_FORM, pubkey, "essay")
        val naddr = Nip19.encodeNaddr(coordinate)

        assertEquals(coordinate, Nip19.coordinateFromUri("nostr:$naddr"))
        assertEquals(coordinate, Nip19.coordinateFromUri("athena://book/$naddr"))
    }

    @Test
    fun `rejects a corrupted identifier instead of returning garbage`() {
        val naddr = Nip19.encodeNaddr(Coordinate(Kinds.LONG_FORM, pubkey, "essay"))
        assertNull(Nip19.decodeNaddr(naddr.dropLast(1) + "q"))
    }
}
