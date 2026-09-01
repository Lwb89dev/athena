package com.athena.reader.platform

import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.relay.parseRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LimitsTest {

    @Test
    fun `zip paths that would escape the archive are refused`() {
        assertTrue(!isSafeArchivePath("../etc/passwd"))
        assertTrue(!isSafeArchivePath("/etc/passwd"))
        assertTrue(!isSafeArchivePath("OEBPS/../../secret"))
        assertTrue(!isSafeArchivePath(""))
        assertTrue(isSafeArchivePath("OEBPS/chapter.xhtml"))
        assertTrue(isSafeArchivePath("META-INF/container.xml"))
    }

    @Test
    fun `relay urls with credentials or the wrong scheme are refused`() {
        assertNotNull(parseRelayUrl("wss://relay.example.com"))
        assertNotNull(parseRelayUrl("wss://relay.example.com/"))
        assertNotNull(parseRelayUrl("  ws://127.0.0.1:7777  "))
        assertNull(parseRelayUrl("https://relay.example.com"))
        assertNull(parseRelayUrl("wss://user:pass@relay.example.com"))
        assertNull(parseRelayUrl("wss://"))
        assertNull(parseRelayUrl("javascript:alert(1)"))
        assertNull(parseRelayUrl("wss://relay.example.com/path\nX"))
    }

    @Test
    fun `coordinates require a 64-char hex pubkey`() {
        val pubkey = "a".repeat(64)
        assertNotNull(Coordinate.parse("30040:$pubkey:book"))
        assertEquals(pubkey, Coordinate.parse("30040:${pubkey.uppercase()}:book")?.pubkey)
        assertNull(Coordinate.parse("30040:not-a-key:book"))
        assertNull(Coordinate.parse("30040:${"a".repeat(63)}:book"))
        assertNull(Coordinate.parse("30040:$pubkey:${"x".repeat(300)}"))
    }
}
