package com.athena.reader.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.session.Session
import com.athena.reader.data.session.SessionStore
import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.relay.RelayCatalog
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.relay.parseRelayUrl
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Limits
import com.athena.reader.ui.UiString
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.error_bunker
import athena.shared.generated.resources.error_pick_relay
import athena.shared.generated.resources.error_relay_url
import athena.shared.generated.resources.error_signer
import athena.shared.generated.resources.error_too_many_relays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OnboardingStep { Welcome, Login, Relays }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val session: Session = Session.Anonymous,
    val npub: String? = null,
    val amberAvailable: Boolean = false,
    val selectedRelays: Set<String> = RelayCatalog.defaultUrls,
    val extraRelays: List<String> = emptyList(),
    val error: UiString? = null,
    val authUrl: String? = null,
    val busy: Boolean = false,
) {
    val canFinish: Boolean get() = selectedRelays.isNotEmpty()
}

class OnboardingViewModel(
    private val signerManager: SignerManager,
    private val sessionStore: SessionStore,
    private val relayPool: RelayPool,
) : ViewModel() {

    private val step = MutableStateFlow(OnboardingStep.Welcome)
    private val selected = MutableStateFlow(RelayCatalog.defaultUrls)
    private val extras = MutableStateFlow<List<String>>(emptyList())
    private val error = MutableStateFlow<UiString?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<OnboardingUiState> = combine(
        combine(step, selected, extras) { page, relays, extra -> Triple(page, relays, extra) },
        combine(error, busy, signerManager.session) { err, wait, session -> Triple(err, wait, session) },
        signerManager.authUrl,
    ) { page, auth, url ->
        val (current, relays, extra) = page
        val (err, wait, session) = auth
        OnboardingUiState(
            step = current,
            session = session,
            npub = session.pubkeyHex?.let(Nip19::encodeNpub),
            amberAvailable = signerManager.isExternalSignerAvailable(),
            selectedRelays = relays,
            extraRelays = extra,
            error = err,
            authUrl = url,
            busy = wait,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun next() {
        step.value = when (step.value) {
            OnboardingStep.Welcome -> OnboardingStep.Login
            OnboardingStep.Login -> OnboardingStep.Relays
            OnboardingStep.Relays -> OnboardingStep.Relays
        }
        error.value = null
    }

    fun back() {
        step.value = when (step.value) {
            OnboardingStep.Welcome -> OnboardingStep.Welcome
            OnboardingStep.Login -> OnboardingStep.Welcome
            OnboardingStep.Relays -> OnboardingStep.Login
        }
        error.value = null
    }

    fun skipLogin() = next()

    fun loginWithAmber() {
        viewModelScope.launch {
            busy.value = true
            signerManager.loginWithExternalSigner()
                .onSuccess { next() }
                .onFailure { failure ->
                    error.value = failure.message?.let { UiString.Literal(it) }
                        ?: UiString.Res(Res.string.error_signer)
                }
            busy.value = false
        }
    }

    fun loginWithBunker(connectionString: String) {
        viewModelScope.launch {
            busy.value = true
            signerManager.loginWithBunker(connectionString.trim())
                .onSuccess { next() }
                .onFailure { failure ->
                    error.value = failure.message?.let { UiString.Literal(it) }
                        ?: UiString.Res(Res.string.error_bunker)
                }
            busy.value = false
        }
    }

    fun toggleRelay(url: String, checked: Boolean) {
        val current = selected.value
        selected.value = if (checked) {
            if (current.size >= Limits.MAX_RELAYS) {
                error.value = UiString.Res(Res.string.error_too_many_relays, listOf(Limits.MAX_RELAYS))
                current
            } else {
                current + url
            }
        } else {
            current - url
        }
    }

    fun addCustomRelay(raw: String): Boolean {
        val normalized = parseRelayUrl(raw)
        if (normalized == null) {
            error.value = UiString.Res(Res.string.error_relay_url)
            return false
        }
        val current = selected.value
        if (current.size >= Limits.MAX_RELAYS) {
            error.value = UiString.Res(Res.string.error_too_many_relays, listOf(Limits.MAX_RELAYS))
            return false
        }
        selected.value = current + normalized
        if (RelayCatalog.onboarding.none { it.url == normalized }) {
            extras.value = (extras.value + normalized).distinct()
        }
        error.value = null
        return true
    }

    fun finish() {
        val urls = selected.value
        if (urls.isEmpty()) {
            error.value = UiString.Res(Res.string.error_pick_relay)
            return
        }
        viewModelScope.launch {
            sessionStore.saveRelays(urls)
            relayPool.setRelays(urls.toList())
            sessionStore.markOnboardingComplete()
        }
    }

    fun dismissError() {
        error.value = null
    }

    fun dismissAuthUrl() = signerManager.dismissAuthUrl()
}
