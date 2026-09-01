package com.athena.reader.di

import com.athena.reader.data.session.SessionStore
import com.athena.reader.data.sync.EncryptedSync
import com.athena.reader.data.sync.SyncSecret
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Connects the relay pool at startup and keeps it in step with the user's relay
 * list. Runs once, owned by the app scope — no screen should have to remember
 * to do this.
 */
class SessionBootstrap(
    private val sessionStore: SessionStore,
    private val relayPool: RelayPool,
    private val syncSecret: SyncSecret,
    private val encryptedSync: EncryptedSync,
    private val scope: CoroutineScope,
) {
    fun start(signerManager: SignerManager) {
        scope.launch {
            combine(sessionStore.relays, signerManager.session) { relays, _ -> relays }
                .collect { relays ->
                    Log.d(TAG, "connecting to ${relays.size} relays")
                    relayPool.setRelays(relays.toList())
                }
        }
        scope.launch { forgetSyncStateOnIdentityChange(signerManager) }
    }

    /**
     * The blinding secret and the anti-rollback marks belong to one identity.
     * Carrying them across a logout would let the next user's slots be judged
     * against the previous user's high-water marks, and silently reject their
     * real data as stale.
     */
    private suspend fun forgetSyncStateOnIdentityChange(signerManager: SignerManager) {
        var previous = signerManager.session.value.pubkeyHex
        signerManager.session.collect { session ->
            if (session.pubkeyHex == previous) return@collect
            previous = session.pubkeyHex
            syncSecret.clear()
            encryptedSync.forgetAll()
            Log.d(TAG, "identity changed, sync state cleared")
        }
    }

    private companion object {
        const val TAG = "SessionBootstrap"
    }
}
