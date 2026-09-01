package com.athena.reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.repository.HighlightRepository
import com.athena.reader.data.repository.LibraryRepository
import com.athena.reader.data.repository.ProgressRepository
import com.athena.reader.data.session.ReaderPrefsStore
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.Highlight
import com.athena.reader.domain.model.HighlightColor
import com.athena.reader.domain.model.HighlightVisibility
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.domain.model.Section
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.nowSeconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReaderUiState(
    val book: Book? = null,
    val sections: List<Section> = emptyList(),
    val sectionIndex: Int = 0,
    val highlights: List<Highlight> = emptyList(),
    /**
     * Detected once when the book loads, not derived on read: detection runs a
     * regex over the whole chapter, and a getter here would re-run it on every
     * recomposition that touches this state.
     */
    val markup: Markup = Markup.Plain,
    val isLoading: Boolean = true,
    val canHighlight: Boolean = false,
    /** Where to restore the scroll to, as a character offset in the section. */
    val restoreOffset: Int = 0,
) {
    val currentSection: Section? get() = sections.getOrNull(sectionIndex)

    val hasNext: Boolean get() = sectionIndex < sections.lastIndex
    val hasPrevious: Boolean get() = sectionIndex > 0
}

/**
 * Owns one book while it is open: its text, the user's markers on it, and where
 * they are in it.
 *
 * Reading position is written locally on every move but pushed to relays only
 * when the reader is left ([onLeaveReader]). A NIP-78 event per page turn would
 * mean a signer round-trip per page turn, which is unusable.
 */
class ReaderViewModel(
    private val libraryRepository: LibraryRepository,
    private val highlightRepository: HighlightRepository,
    private val progressRepository: ProgressRepository,
    private val signerManager: SignerManager,
    private val readerPrefs: ReaderPrefsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var coordinate: Coordinate? = null
    private var progressJob: Job? = null

    fun load(target: Coordinate) {
        if (coordinate == target) return
        coordinate = target

        viewModelScope.launch {
            _uiState.value = ReaderUiState(isLoading = true)
            runCatching { openBook(target) }
                .onFailure { _uiState.value = ReaderUiState(isLoading = false) }
        }
    }

    private suspend fun openBook(target: Coordinate) {
        // Cache first so a reopened book is instant; the relays are the
        // fallback, not the fast path.
        val book = libraryRepository.observeBook(target).first()
            ?: libraryRepository.fetchBook(target)

        if (book == null) {
            _uiState.value = ReaderUiState(isLoading = false)
            return
        }

        val sections = libraryRepository.fetchSections(book)
        val resumed = progressRepository.pull(target)
            ?: progressRepository.observe(target).first()

        _uiState.value = ReaderUiState(
            book = book,
            sections = sections,
            markup = detectMarkup(book, sections),
            sectionIndex = resumed?.sectionIndex?.coerceIn(0, sections.lastIndex.coerceAtLeast(0)) ?: 0,
            isLoading = false,
            canHighlight = signerManager.current.value.canSign,
            restoreOffset = resumed?.charOffset ?: 0,
        )
        observeHighlights(target)
        readerPrefs.setLastBook(target)
        val pubkey = signerManager.current.value.pubkeyHex
        if (pubkey != null) {
            viewModelScope.launch { runCatching { highlightRepository.syncFrom(pubkey) } }
        }
    }

    /**
     * NKBIP-01 sections are AsciiDoc, NIP-23 articles Markdown. The book's kind
     * settles it, so the reader never has to guess from the text alone.
     */
    private fun detectMarkup(book: Book, sections: List<Section>): Markup =
        MarkupRenderer.detect(
            content = sections.firstOrNull()?.content.orEmpty(),
            isAsciiDoc = book.coordinate.kind == Kinds.PUBLICATION_INDEX,
        )

    private fun observeHighlights(target: Coordinate) {
        viewModelScope.launch {
            highlightRepository.observeForBook(target).collect { highlights ->
                _uiState.value = _uiState.value.copy(highlights = highlights)
            }
        }
    }

    fun goToSection(index: Int) {
        val state = _uiState.value
        if (index !in state.sections.indices) return
        _uiState.value = state.copy(sectionIndex = index, restoreOffset = 0)
        recordProgress(charOffset = 0)
    }

    fun showPosition(sectionIndex: Int, charOffset: Int) {
        val state = _uiState.value
        if (sectionIndex !in state.sections.indices) return
        if (state.sectionIndex != sectionIndex) {
            _uiState.value = state.copy(sectionIndex = sectionIndex)
        }
        recordProgress(charOffset)
    }

    fun next() = goToSection(_uiState.value.sectionIndex + 1)

    fun previous() = goToSection(_uiState.value.sectionIndex - 1)

    /** Called as the user scrolls; debounced so we do not hammer the database. */
    fun onScrolled(charOffset: Int) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(PROGRESS_DEBOUNCE_MILLIS)
            recordProgress(charOffset)
        }
    }

    /** Publishes whatever the local row holds. Call this when leaving the reader. */
    fun onLeaveReader() {
        viewModelScope.launch { progressRepository.syncPending() }
    }

    fun highlight(
        selection: IntRange,
        text: String,
        color: HighlightColor,
        visibility: HighlightVisibility,
    ) {
        val state = _uiState.value
        val book = state.book ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            highlightRepository.create(
                Highlight(
                    id = "",
                    bookCoordinate = book.coordinate,
                    sectionCoordinate = state.currentSection?.coordinate,
                    text = trimmed,
                    context = contextAround(selection),
                    startOffset = selection.first,
                    endOffset = selection.last + 1,
                    color = color,
                    visibility = visibility,
                    authorPubkey = signerManager.current.value.pubkeyHex.orEmpty(),
                    createdAt = nowSeconds(),
                ),
            )
        }
    }

    fun removeHighlight(highlight: Highlight) {
        viewModelScope.launch { highlightRepository.delete(highlight) }
    }

    /**
     * NIP-84's `context` tag: enough surrounding text to relocate the passage if
     * the author republishes the section with edits.
     */
    private fun contextAround(selection: IntRange): String? {
        val content = _uiState.value.currentSection?.content ?: return null
        val start = (selection.first - CONTEXT_CHARS).coerceAtLeast(0)
        val end = (selection.last + CONTEXT_CHARS).coerceAtMost(content.length)
        if (start >= end) return null
        return content.substring(start, end).trim()
    }

    private fun recordProgress(charOffset: Int) {
        val state = _uiState.value
        val book = state.book ?: return
        val sectionLength = state.currentSection?.content?.length ?: return

        val sectionFraction = if (sectionLength == 0) 0f else charOffset.toFloat() / sectionLength
        val overall = (state.sectionIndex + sectionFraction) / state.sections.size.coerceAtLeast(1)

        viewModelScope.launch {
            progressRepository.saveLocal(
                ReadingProgress(
                    bookCoordinate = book.coordinate,
                    sectionIndex = state.sectionIndex,
                    charOffset = charOffset,
                    updatedAt = nowSeconds(),
                    fraction = overall.coerceIn(0f, 1f),
                ),
            )
        }
    }

    private companion object {
        const val PROGRESS_DEBOUNCE_MILLIS = 1_500L
        const val CONTEXT_CHARS = 120
    }
}
