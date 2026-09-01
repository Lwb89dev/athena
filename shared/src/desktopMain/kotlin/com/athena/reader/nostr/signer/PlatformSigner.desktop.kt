package com.athena.reader.nostr.signer

import com.athena.reader.data.session.Session

/**
 * The desktop has no NIP-55 signer app to talk to — that is precisely the gap
 * NIP-46 fills. This factory answers "no external signer here", which makes the
 * login screen offer the bunker field instead.
 *
 * Bunker sessions are not restored here: they need a relay pool and the stored
 * session key, so [SignerManager] owns that path for both platforms rather than
 * each factory reimplementing it.
 */
class DesktopSignerFactory : PlatformSignerFactory {
    override fun supportsExternalSigner(): Boolean = false
    override suspend fun connectExternalSigner(): NostrSigner? = null
    override fun restoreExternalSigner(session: Session): NostrSigner? = null
}
