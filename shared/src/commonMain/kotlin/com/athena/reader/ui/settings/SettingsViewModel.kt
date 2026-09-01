package com.athena.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.session.Session
import com.athena.reader.data.session.SessionStore
import com.athena.reader.data.session.SyncMode
import com.athena.reader.data.sync.SyncSecret
import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.ui.UiString
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.error_bunker
import athena.shared.generated.resources.error_passphrase_short
import athena.shared.generated.resources.error_relay_url
import athena.shared.generated.resources.error_sign_in_first
import athena.shared.generated.resources.error_signer
import athena.shared.generated.resources.error_too_many_relays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val session: Session = Session.Anonymous,
    val npub: String? = null,
    val relays: List<String> = emptyList(),
    val externalSignerAvailable: Boolean = false,
    val error: UiString? = null,
    /** Set when a bunker wants the user to approve this app in a browser. */
    val authUrl: String? = null,
    val syncMode: SyncMode = SyncMode.Off,
    /** False in passphrase mode until this device has been unlocked. */
    val syncUnlocked: Boolean = false,
    val busy: Boolean = false,
)

class SettingsViewModel(
    private val signerManager: SignerManager,
    private val sessionStore: SessionStore,
    private val relayPool: RelayPool,
    private val syncSecret: SyncSecret,
) : ViewModel() {

    private val error = MutableStateFlow<UiString?>(null)
    private val busy = MutableStateFlow(false)
    private val unlocked = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        signerManager.session,
        sessionStore.session,
        sessionStore.relays,
        error,
        signerManager.authUrl,
        unlocked,
        busy,
    ) { values ->
        // `combine` beyond five flows hands back an untyped array, so the casts
        // are unavoidable; they are all done here rather than inline so the
        // constructor call below stays readable.
        @Suppress("UNCHECKED_CAST")
        val relays = (values[2] as Set<String>).sorted()
        val session = values[0] as Session
        val stored = values[1] as Session

        SettingsUiState(
            session = session,
            npub = session.pubkeyHex?.let(Nip19::encodeNpub),
            relays = relays,
            externalSignerAvailable = signerManager.isExternalSignerAvailable(),
            error = values[3] as UiString?,
            authUrl = values[4] as String?,
            syncMode = stored.syncMode,
            syncUnlocked = values[5] as Boolean,
            busy = values[6] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { unlocked.value = syncSecret.isUnlocked() }
    }

    fun setSyncMode(mode: SyncMode) {
        viewModelScope.launch {
            syncSecret.setMode(mode)
            unlocked.value = syncSecret.isUnlocked()
        }
    }

    /**
     * Deriving the secret is deliberately slow (600k PBKDF2 rounds), so the
     * screen shows a spinner rather than appearing frozen.
     */
    fun unlockSync(passphrase: String) {
        if (passphrase.length < MIN_PASSPHRASE) {
            error.value = UiString.Res(Res.string.error_passphrase_short, listOf(MIN_PASSPHRASE))
            return
        }
        viewModelScope.launch {
            busy.value = true
            try {
                val ok = syncSecret.unlockWithPassphrase(passphrase)
                unlocked.value = ok
                if (!ok) error.value = UiString.Res(Res.string.error_sign_in_first)
            } finally {
                busy.value = false
            }
        }
    }

    fun loginWithAmber() {
        viewModelScope.launch {
            signerManager.loginWithExternalSigner().onFailure { failure ->
                error.value = UiString.Literal(failure.message ?: "")
                    .takeIf { failure.message != null }
                    ?: UiString.Res(Res.string.error_signer)
            }
        }
    }

    fun loginWithBunker(connectionString: String) {
        viewModelScope.launch {
            signerManager.loginWithBunker(connectionString.trim()).onFailure { failure ->
                error.value = UiString.Literal(failure.message ?: "")
                    .takeIf { failure.message != null }
                    ?: UiString.Res(Res.string.error_bunker)
            }
        }
    }

    fun logout() {
        viewModelScope.launch { signerManager.logout() }
    }

    fun addRelay(url: String) {
        val normalized = com.athena.reader.nostr.relay.parseRelayUrl(url)
        if (normalized == null) {
            error.value = UiString.Res(Res.string.error_relay_url)
            return
        }
        updateRelays { current ->
            if (current.size >= com.athena.reader.platform.Limits.MAX_RELAYS) {
                error.value = UiString.Res(
                    Res.string.error_too_many_relays,
                    listOf(com.athena.reader.platform.Limits.MAX_RELAYS),
                )
                current
            } else {
                current + normalized
            }
        }
    }

    fun removeRelay(url: String) = updateRelays { it - url }

    fun dismissError() {
        error.value = null
    }

    fun dismissAuthUrl() = signerManager.dismissAuthUrl()

    private companion object {
        const val MIN_PASSPHRASE = 12
    }

    private fun updateRelays(transform: (Set<String>) -> Set<String>) {
        viewModelScope.launch {
            val current = uiState.value.relays.toSet()
            val updated = transform(current)
            sessionStore.saveRelays(updated)
            relayPool.setRelays(updated.toList())
        }
    }
}
