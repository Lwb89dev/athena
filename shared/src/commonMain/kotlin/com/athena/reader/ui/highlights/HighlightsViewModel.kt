package com.athena.reader.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.repository.HighlightRepository
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.HighlightVisibility
import com.athena.reader.nostr.signer.SignerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HighlightFilter { All, Public, Private }

data class HighlightsUiState(
    val highlights: List<Highlight> = emptyList(),
    val filter: HighlightFilter = HighlightFilter.All,
    val isLoggedIn: Boolean = false,
)

/**
 * The user's own shelf of marked passages — the "favourites" screen.
 *
 * Whether an entry is public is a property of the entry, not of the screen: a
 * public highlight is already a NIP-84 event anyone can find, and a private one
 * never left the device unencrypted. The filter here is just a lens on that.
 */
class HighlightsViewModel(
    private val highlightRepository: HighlightRepository,
    private val signerManager: SignerManager,
) : ViewModel() {

    private val filter = MutableStateFlow(HighlightFilter.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mine = signerManager.session.flatMapLatest { session ->
        session.pubkeyHex?.let(highlightRepository::observeMine) ?: flowOf(emptyList())
    }

    val uiState: StateFlow<HighlightsUiState> = combine(
        mine,
        filter,
        signerManager.session,
    ) { highlights, activeFilter, session ->
        HighlightsUiState(
            highlights = highlights.filter { it.matches(activeFilter) },
            filter = activeFilter,
            isLoggedIn = session.isLoggedIn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HighlightsUiState())

    init {
        refresh()
    }

    fun setFilter(value: HighlightFilter) {
        filter.value = value
    }

    fun refresh() {
        val pubkey = signerManager.session.value.pubkeyHex ?: return
        viewModelScope.launch { highlightRepository.syncFrom(pubkey) }
    }

    fun toggleVisibility(highlight: Highlight) {
        val target = if (highlight.visibility == HighlightVisibility.Public) {
            HighlightVisibility.Private
        } else {
            HighlightVisibility.Public
        }
        viewModelScope.launch { highlightRepository.setVisibility(highlight, target) }
    }

    fun delete(highlight: Highlight) {
        viewModelScope.launch { highlightRepository.delete(highlight) }
    }

    private fun Highlight.matches(filter: HighlightFilter): Boolean = when (filter) {
        HighlightFilter.All -> true
        HighlightFilter.Public -> visibility == HighlightVisibility.Public
        HighlightFilter.Private -> visibility == HighlightVisibility.Private
    }
}
