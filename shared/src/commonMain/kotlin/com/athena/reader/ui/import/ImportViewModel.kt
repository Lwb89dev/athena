package com.athena.reader.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.importer.BookImporter
import com.athena.reader.data.importer.BookPublisher
import com.athena.reader.data.importer.EpubImporter
import com.athena.reader.data.importer.ImportedBook
import com.athena.reader.data.importer.PdfImporter
import com.athena.reader.data.importer.PublishResult
import com.athena.reader.data.importer.PublishVisibility
import com.athena.reader.data.importer.TextImporter
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Limits
import com.athena.reader.platform.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val book: ImportedBook? = null,
    val visibility: PublishVisibility = PublishVisibility.Public,
    val isParsing: Boolean = false,
    val isPublishing: Boolean = false,
    val publishingDone: Int = 0,
    val publishingTotal: Int = 0,
    val error: String? = null,
    val published: Coordinate? = null,
    val canPublish: Boolean = false,
    val readCoordinate: Coordinate? = null,
) {
    val hasBook: Boolean get() = book != null
    val canRead: Boolean get() = book?.isPublishable == true
}

/**
 * The upload side of the gateway: a file in, a book on nostr out.
 *
 * Metadata is editable before anything is published, because that is the step
 * that decides whether the book is findable later. A file called `pg1342.epub`
 * with no author tag is not a library entry, it is a blob.
 */
class ImportViewModel(
    private val publisher: BookPublisher,
    private val signerManager: SignerManager,
) : ViewModel() {

    private val importers: List<BookImporter> = listOf(EpubImporter(), PdfImporter(), TextImporter())

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    val supportedExtensions: Set<String> = importers.flatMap { it.extensions }.toSet()

    /** Parses on a background dispatcher: a large EPUB would jank the UI. */
    fun load(fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.value = ImportUiState(isParsing = true)

            if (bytes.size > Limits.MAX_FILE_BYTES) {
                val mb = Limits.MAX_FILE_BYTES / (1024 * 1024)
                _uiState.value = ImportUiState(error = "That file is larger than $mb MB.")
                return@launch
            }

            val importer = importers.firstOrNull { it.canHandle(fileName) }
            if (importer == null) {
                _uiState.value = ImportUiState(
                    error = "Athena does not read ${fileName.substringAfterLast('.', "that")} " +
                        "files yet. Try PDF, EPUB or plain text.",
                )
                return@launch
            }

            val parsed = withContext(ioDispatcher) {
                runCatching { importer.import(fileName, bytes) }.getOrNull()
            }
            _uiState.value = if (parsed == null) {
                val scannedPdf = fileName.substringAfterLast('.').equals("pdf", ignoreCase = true)
                ImportUiState(
                    error = if (scannedPdf) {
                        "This PDF has no extractable text. Scanned pages cannot go on the relays."
                    } else {
                        "That file could not be read as a book."
                    },
                )
            } else {
                val fitted = parsed.fitRelayLimits()
                val shelved = fitted.copy(localSlug = publisher.slugFor(fitted))
                ImportUiState(book = shelved, canPublish = shelved.isPublishable)
            }
        }
    }

    fun reportError(message: String) {
        _uiState.value = _uiState.value.copy(error = message, isParsing = false, isPublishing = false)
    }

    fun editTitle(value: String) = edit { it.copy(title = value) }

    fun editAuthor(value: String) = edit { it.copy(author = value) }

    fun editSummary(value: String) = edit { it.copy(summary = value) }

    fun editLanguage(value: String) = edit { it.copy(language = value) }

    fun editTopics(value: String) = edit { book ->
        book.copy(topics = value.split(',').map(String::trim).filter(String::isNotBlank))
    }

    fun setVisibility(value: PublishVisibility) {
        _uiState.value = _uiState.value.copy(visibility = value)
    }

    /** Writes the book to the local library, then opens the reader. */
    fun openReader(open: (Coordinate) -> Unit) {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val slug = book.localSlug ?: publisher.slugFor(book)
            val shelved = book.copy(localSlug = slug)
            val coordinate = runCatching {
                publisher.shelveLocally(shelved, BookPublisher.LOCAL_SHELF_PUBKEY)
            }
                .getOrElse { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Could not open the book.",
                    )
                    return@launch
                }
            _uiState.value = _uiState.value.copy(book = shelved, readCoordinate = coordinate)
            open(coordinate)
        }
    }

    fun publish() {
        val book = _uiState.value.book ?: return
        if (!signerManager.current.value.canSign) {
            _uiState.value = _uiState.value.copy(error = "Sign in before publishing a book.")
            return
        }

        viewModelScope.launch {
            val total = book.sections.size + 1
            _uiState.value = _uiState.value.copy(
                isPublishing = true,
                error = null,
                publishingDone = 0,
                publishingTotal = total,
            )
            val outcome = runCatching {
                publisher.publish(book, _uiState.value.visibility) { done, all ->
                    _uiState.value = _uiState.value.copy(publishingDone = done, publishingTotal = all)
                }
            }
            _uiState.value = finishPublish(_uiState.value, outcome)
        }
    }

    private fun finishPublish(
        current: ImportUiState,
        outcome: Result<PublishResult>,
    ): ImportUiState {
        val result = outcome.getOrElse { error ->
            return current.copy(isPublishing = false, error = error.message ?: "Publishing failed.")
        }
        return when (result) {
            is PublishResult.Success -> current.copy(
                isPublishing = false,
                published = result.coordinate,
                readCoordinate = result.coordinate,
            )
            is PublishResult.Failure -> current.copy(isPublishing = false, error = result.reason)
        }
    }

    fun reset() {
        _uiState.value = ImportUiState()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun edit(transform: (ImportedBook) -> ImportedBook) {
        val current = _uiState.value.book ?: return
        val updated = transform(current)
        _uiState.value = _uiState.value.copy(book = updated, canPublish = updated.isPublishable)
    }
}
