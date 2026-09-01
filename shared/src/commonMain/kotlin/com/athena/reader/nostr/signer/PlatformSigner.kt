package com.athena.reader.nostr.signer

import com.athena.reader.data.session.Session

/**
 * How a platform offers a signer.
 *
 * Android has NIP-55: a real signer app (Amber) sitting next to us, reachable by
 * intent. The desktop has nothing equivalent, which is exactly why NIP-46 exists
 * there. Keeping that difference behind this one interface means [SignerManager]
 * and everything above it never branches on platform.
 */
interface PlatformSignerFactory {

    /** True when a NIP-55 signer app is installed and can be asked for a key. */
    fun supportsExternalSigner(): Boolean

    /** Runs the NIP-55 handshake. Null when the user backed out. */
    suspend fun connectExternalSigner(): NostrSigner?

    /** Rebuilds a signer for a stored session without prompting the user. */
    fun restoreExternalSigner(session: Session): NostrSigner?
}
