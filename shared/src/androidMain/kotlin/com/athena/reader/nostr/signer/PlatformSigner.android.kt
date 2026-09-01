package com.athena.reader.nostr.signer

import com.athena.reader.data.session.Session
import com.athena.reader.platform.AndroidPlatform
import kotlinx.serialization.json.Json

/**
 * On Android the external signer is a real app next door (Amber), so the
 * factory is a thin wrapper around the NIP-55 intent bridge.
 */
class AndroidSignerFactory(
    private val launcher: ExternalSignerLauncher,
    private val json: Json,
) : PlatformSignerFactory {

    override fun supportsExternalSigner(): Boolean =
        AmberSigner.isAvailable(AndroidPlatform.applicationContext)

    override suspend fun connectExternalSigner(): NostrSigner? {
        val signer = AmberSigner(AndroidPlatform.applicationContext, launcher, json)
        return signer.takeIf { it.requestPublicKey().isSuccess }
    }

    override fun restoreExternalSigner(session: Session): NostrSigner? {
        val pubkey = session.pubkeyHex ?: return null
        return AmberSigner(
            context = AndroidPlatform.applicationContext,
            launcher = launcher,
            json = json,
            knownPubkey = pubkey,
            signerApp = session.signerPackage ?: AmberSigner.DEFAULT_SIGNER_PACKAGE,
        )
    }
}
