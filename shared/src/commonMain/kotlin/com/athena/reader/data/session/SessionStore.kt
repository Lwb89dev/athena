package com.athena.reader.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class LoginMethod { None, Amber, Bunker }

/**
 * How the blinding secret for private sync is obtained — and therefore what,
 * if anything, a relay can learn about this user from the app's existence.
 */
enum class SyncMode {
    /** No private sync. Nothing about private data reaches a relay. */
    Off,

    /** Derived from a passphrase. Nothing is published, nothing is probeable. */
    Passphrase,

    /** Published in one event at a computable address. Convenient, probeable. */
    RelayBootstrap,
}

data class Session(
    val pubkeyHex: String?,
    val method: LoginMethod,
    /** Which NIP-55 app answered, so later calls go back to the same one. */
    val signerPackage: String?,
    /** The bunker:// URI, for NIP-46 sessions. */
    val bunkerUri: String? = null,
    /**
     * The NIP-46 *session* private key. Not the user's identity key: it is a
     * per-installation credential the bunker has authorised, closer to an OAuth
     * refresh token. It still grants signing until revoked, so it is the one
     * secret this app stores.
     */
    val sessionKeyHex: String? = null,
    val syncMode: SyncMode = SyncMode.Off,
    /** The *derived* blinding secret, never the passphrase it came from. */
    val syncSecretHex: String? = null,
) {
    val isLoggedIn: Boolean get() = pubkeyHex != null

    companion object {
        val Anonymous = Session(null, LoginMethod.None, null)
    }
}

/**
 * Session state on disk.
 *
 * For NIP-55 (Amber) this is public information only: which npub, which signer
 * app. For NIP-46 it also holds the session key described in [Session] — a
 * bunker session cannot survive a restart without it.
 *
 * That key is stored unencrypted, which is the honest MVP position: on Android
 * it is inside app-private storage, on the desktop inside the user's data
 * directory. Hardening it means the platform keystore, and is tracked in
 * docs/ARCHITECTURE.md rather than pretended away here.
 */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
) {
    val session: Flow<Session> = dataStore.data.map { prefs ->
        Session(
            pubkeyHex = prefs[KEY_PUBKEY],
            method = prefs[KEY_METHOD]?.let { name ->
                LoginMethod.entries.firstOrNull { it.name == name }
            } ?: LoginMethod.None,
            signerPackage = prefs[KEY_SIGNER_PACKAGE],
            bunkerUri = prefs[KEY_BUNKER],
            sessionKeyHex = prefs[KEY_SESSION_KEY],
            syncMode = prefs[KEY_SYNC_MODE]?.let { name ->
                SyncMode.entries.firstOrNull { it.name == name }
            } ?: SyncMode.Off,
            syncSecretHex = prefs[KEY_SYNC_SECRET],
        )
    }

    val relays: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_RELAYS]?.takeIf { it.isNotEmpty() } ?: DEFAULT_RELAYS
    }

    val onboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] == true
    }

    suspend fun save(session: Session) {
        dataStore.edit { prefs ->
            prefs.put(KEY_PUBKEY, session.pubkeyHex)
            prefs[KEY_METHOD] = session.method.name
            prefs.put(KEY_SIGNER_PACKAGE, session.signerPackage)
            prefs.put(KEY_BUNKER, session.bunkerUri)
            prefs.put(KEY_SESSION_KEY, session.sessionKeyHex)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PUBKEY)
            prefs.remove(KEY_SIGNER_PACKAGE)
            prefs.remove(KEY_BUNKER)
            prefs.remove(KEY_SESSION_KEY)
            prefs.remove(KEY_SYNC_SECRET)
            prefs[KEY_SYNC_MODE] = SyncMode.Off.name
            prefs[KEY_METHOD] = LoginMethod.None.name
        }
    }

    suspend fun saveSyncMode(mode: SyncMode) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_MODE] = mode.name
            // Leaving a stale secret behind would silently keep publishing to
            // slots the user believes they have turned off.
            if (mode != SyncMode.Passphrase) prefs.remove(KEY_SYNC_SECRET)
        }
    }

    suspend fun saveSyncSecret(secretHex: String, mode: SyncMode) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_SECRET] = secretHex
            prefs[KEY_SYNC_MODE] = mode.name
        }
    }

    suspend fun saveRelays(urls: Set<String>) {
        dataStore.edit { prefs -> prefs[KEY_RELAYS] = urls }
    }

    suspend fun markOnboardingComplete() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }

    private fun MutablePreferences.put(key: Preferences.Key<String>, value: String?) {
        if (value == null) remove(key) else set(key, value)
    }

    companion object {
        private val KEY_PUBKEY = stringPreferencesKey("pubkey_hex")
        private val KEY_METHOD = stringPreferencesKey("login_method")
        private val KEY_SIGNER_PACKAGE = stringPreferencesKey("signer_package")
        private val KEY_BUNKER = stringPreferencesKey("bunker_uri")
        private val KEY_SESSION_KEY = stringPreferencesKey("nip46_session_key")
        private val KEY_SYNC_MODE = stringPreferencesKey("sync_mode")
        private val KEY_SYNC_SECRET = stringPreferencesKey("sync_secret")
        private val KEY_RELAYS = stringSetPreferencesKey("relays")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        /**
         * Bootstrap relays. The first one carries most NKBIP-01 publications
         * today; once the user logs in we merge in their NIP-65 list.
         *
         * `relay.nostr.band` is deliberately absent despite being excellent for
         * discovery: it feeds a public search indexer, and every encrypted sync
         * snapshot published here would be permanently archived by it. A blinded
         * address does not help against an archivist that keeps everything
         * forever. The user can still add it by hand if they want the reach.
         */
        val DEFAULT_RELAYS = setOf(
            "wss://thecitadel.nostr1.com",
            "wss://nostr.wine",
            "wss://relay.damus.io",
            "wss://nos.lol",
        )
    }
}
