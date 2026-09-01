package com.athena.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.repository.LibraryRepository
import com.athena.reader.data.repository.ProgressRepository
import com.athena.reader.data.session.ReaderPrefs
import com.athena.reader.data.session.ReaderPrefsStore
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.nostr.model.Coordinate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderHubUiState(
    val lastBook: Book? = null,
    val continueReading: List<Pair<Book, ReadingProgress>> = emptyList(),
    val prefs: ReaderPrefs = ReaderPrefs(),
)

class ReaderHubViewModel(
    private val libraryRepository: LibraryRepository,
    private val progressRepository: ProgressRepository,
    private val prefsStore: ReaderPrefsStore,
) : ViewModel() {

    val uiState: StateFlow<ReaderHubUiState> = combine(
        libraryRepository.observeLibrary(),
        progressRepository.observeContinueReading(),
        prefsStore.prefs,
    ) { books, progress, prefs ->
        val byCoordinate = books.associateBy { it.coordinate }
        val continued = progress.mapNotNull { entry ->
            byCoordinate[entry.bookCoordinate]?.let { it to entry }
        }
        ReaderHubUiState(
            lastBook = prefs.lastBook?.let { byCoordinate[it] },
            continueReading = continued,
            prefs = prefs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderHubUiState())

    fun updatePrefs(next: ReaderPrefs) {
        viewModelScope.launch { prefsStore.update { next } }
    }

    fun remember(coordinate: Coordinate) {
        viewModelScope.launch { prefsStore.setLastBook(coordinate) }
    }
}
