package com.athena.reader.nostr.relay

import com.athena.reader.data.session.SessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RelayCatalogTest {

    @Test
    fun `onboarding offers eight public relays`() {
        assertEquals(8, RelayCatalog.onboarding.size)
        assertEquals(8, RelayCatalog.onboarding.map { it.url }.toSet().size)
        RelayCatalog.onboarding.forEach { relay ->
            assertNotNull(parseRelayUrl(relay.url), relay.url)
        }
    }

    @Test
    fun `default ticks match the session bootstrap set`() {
        assertEquals(SessionStore.DEFAULT_RELAYS, RelayCatalog.defaultUrls)
        assertTrue(RelayCatalog.defaultUrls.size in 1..8)
    }
}
