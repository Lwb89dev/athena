package com.athena.reader.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.repository.FavoriteRepository
import com.athena.reader.data.repository.HighlightRepository
import com.athena.reader.data.repository.LibraryRepository
import com.athena.reader.data.repository.Profile
import com.athena.reader.data.repository.ProfileRepository
import com.athena.reader.data.repository.ProgressRepository
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.nostr.model.Coordinate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val book: Book? = null,
    val isFavorite: Boolean = false,
    val progress: ReadingProgress? = null,
    /** What other readers marked in this book — NIP-84 makes this free. */
    val communityHighlights: List<Highlight> = emptyList(),
    val author: Profile? = null,
    val isLoading: Boolean = true,
)

class BookDetailViewModel(
    private val libraryRepository: LibraryRepository,
    private val favoriteRepository: FavoriteRepository,
    private val highlightRepository: HighlightRepository,
    private val profileRepository: ProfileRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var coordinate: Coordinate? = null

    fun load(target: Coordinate) {
        if (coordinate == target) return
        coordinate = target

        viewModelScope.launch {
            val book = runCatching {
                libraryRepository.observeBook(target).first()
                    ?: libraryRepository.fetchBook(target)
            }.getOrNull()
            _uiState.value = _uiState.value.copy(book = book, isLoading = false)

            launch { observeFavorite(target) }
            launch { runCatching { loadCommunityHighlights(target) } }
            launch { runCatching { loadAuthor(book?.authorPubkey) } }
            launch { observeProgress(target) }
        }
    }

    fun toggleFavorite(isPrivate: Boolean) {
        val target = coordinate ?: return
        viewModelScope.launch { favoriteRepository.toggle(target, isPrivate) }
    }

    private suspend fun observeFavorite(target: Coordinate) {
        favoriteRepository.observeIsFavorite(target).collect { isFavorite ->
            _uiState.value = _uiState.value.copy(isFavorite = isFavorite)
        }
    }

    /** Drives the "Read" / "Continue reading" label; was declared but never fed. */
    private suspend fun observeProgress(target: Coordinate) {
        progressRepository.observe(target).collect { progress ->
            _uiState.value = _uiState.value.copy(progress = progress)
        }
    }

    private suspend fun loadAuthor(pubkey: String?) {
        val profile = pubkey?.let { profileRepository.get(it) } ?: return
        _uiState.value = _uiState.value.copy(author = profile)
    }

    private suspend fun loadCommunityHighlights(target: Coordinate) {
        val highlights = highlightRepository.communityHighlights(target)
        _uiState.value = _uiState.value.copy(communityHighlights = highlights)
    }
}
