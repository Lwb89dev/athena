package com.athena.reader.nostr.signer

import com.athena.reader.data.session.LoginMethod
import com.athena.reader.data.session.Session
import com.athena.reader.data.session.SessionStore
import com.athena.reader.nostr.crypto.KeyPair
import com.athena.reader.nostr.crypto.toHex
import com.athena.reader.nostr.relay.RelayPool
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * The app's single source of truth for "who am I signing as". Everything else
 * depends on [current], never on a concrete signer implementation — which is
 * what lets Amber on Android and a bunker on the desktop be interchangeable.
 */
class SignerManager(
    private val sessionStore: SessionStore,
    private val platform: PlatformSignerFactory,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val json: Json,
) {
    private val _current = MutableStateFlow<NostrSigner>(AnonymousSigner)
    val current: StateFlow<NostrSigner> = _current

    private val _session = MutableStateFlow(Session.Anonymous)
    val session: StateFlow<Session> = _session

    private val _authUrl = MutableStateFlow<String?>(null)

    /** Set when a bunker asks the user to approve this app in a browser. */
    val authUrl: StateFlow<String?> = _authUrl

    init {
        scope.launch { restore() }
    }

    fun isExternalSignerAvailable(): Boolean = platform.supportsExternalSigner()

    /** NIP-55. The user picks their signer app; we only ever learn the pubkey. */
    suspend fun loginWithExternalSigner(): Result<Session> {
        val signer = platform.connectExternalSigner()
            ?: return Result.failure(SignerDismissed)
        val pubkey = signer.pubkeyHex
            ?: return Result.failure(SignerDismissed)

        val session = Session(
            pubkeyHex = pubkey,
            method = LoginMethod.Amber,
            signerPackage = (signer as? HasSignerPackage)?.signerPackage,
        )
        adopt(signer, session)
        sessionStore.save(session)
        return Result.success(session)
    }

    /**
     * NIP-46. Generates a fresh session key, runs `connect`, and only stores the
     * session once the bunker has actually answered with a pubkey — a half-open
     * session that looks logged-in but cannot sign is worse than none.
     */
    suspend fun loginWithBunker(connectionString: String): Result<Session> {
        val bunker = BunkerUri.parse(connectionString)
            ?: return Result.failure(IllegalArgumentException("Not a bunker:// URI"))

        val sessionKey = KeyPair.generate()
        val signer = buildBunkerSigner(bunker, sessionKey)

        val pubkey = signer.connect().getOrElse { failure ->
            if (failure is BunkerAuthRequired) _authUrl.value = failure.url
            return Result.failure(failure)
        }

        val session = Session(
            pubkeyHex = pubkey,
            method = LoginMethod.Bunker,
            signerPackage = null,
            bunkerUri = connectionString,
            sessionKeyHex = sessionKey.privateKey.toHex(),
        )
        adopt(signer, session)
        sessionStore.save(session)
        return Result.success(session)
    }

    suspend fun logout() {
        adopt(AnonymousSigner, Session.Anonymous)
        _authUrl.value = null
        sessionStore.clear()
    }

    fun dismissAuthUrl() {
        _authUrl.value = null
    }

    /**
     * Restores the identity on cold start without prompting. NIP-55 keeps the
     * key and we only remember which npub it was; NIP-46 rebuilds the session
     * from the stored session key. Either way reads work immediately and the
     * signer is only involved again on the first write.
     */
    private suspend fun restore() {
        val stored = sessionStore.session.first()
        if (stored.pubkeyHex == null) return

        val signer = when (stored.method) {
            LoginMethod.Amber -> platform.restoreExternalSigner(stored)
            LoginMethod.Bunker -> restoreBunker(stored)
            LoginMethod.None -> null
        } ?: return

        adopt(signer, stored)
    }

    private fun restoreBunker(stored: Session): NostrSigner? {
        val bunker = stored.bunkerUri?.let(BunkerUri::parse) ?: return null
        val sessionKey = stored.sessionKeyHex?.let { runCatching { KeyPair.fromHex(it) }.getOrNull() }
            ?: return null

        // The pubkey is already known from the stored session, so the signer is
        // usable immediately — no round-trip before the first read.
        return buildBunkerSigner(bunker, sessionKey, stored.pubkeyHex)
    }

    /** Bunker traffic uses the bunker's own relays, not the ones the user reads on. */
    private fun buildBunkerSigner(
        bunker: BunkerUri,
        sessionKey: KeyPair,
        knownPubkey: String? = null,
    ): Nip46Signer {
        val pool = RelayPool(httpClient, json)
        val signer = Nip46Signer(bunker, sessionKey, pool, json, knownPubkey)
        scope.launch {
            signer.authUrl.collect { url -> if (url != null) _authUrl.value = url }
        }
        return signer
    }

    private fun adopt(signer: NostrSigner, session: Session) {
        _current.value = signer
        _session.value = session
    }
}

/** Implemented by NIP-55 signers so the session can remember which app to reuse. */
interface HasSignerPackage {
    val signerPackage: String
}
