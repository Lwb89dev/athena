package com.athena.reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athena.reader.data.repository.FavoriteRepository
import com.athena.reader.data.repository.FollowRepository
import com.athena.reader.data.repository.LibraryRepository
import com.athena.reader.data.repository.PeopleList
import com.athena.reader.data.repository.Profile
import com.athena.reader.data.repository.ProfileRepository
import com.athena.reader.data.repository.ProgressRepository
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.ReadingProgress
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.signer.SignerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Which slice of nostr the library shows.
 *
 * Not a cosmetic filter. An unfiltered kind 30023 feed is largely spam, a lot
 * of it pornographic, so a global default would make the app unusable as a
 * library on first open. The social graph is the filter — the user's own, not
 * one this app curates on their behalf.
 */
sealed interface FeedScope {
    /** NIP-23 long-form from NIP-02 contacts. Always the default feed. */
    data object Following : FeedScope

    /**
     * Long-form from the next hop: people our follows follow, and people who
     * follow our follows.
     */
    data object Acquaintances : FeedScope

    /** NIP-51 follow set: a curated group, e.g. "classics". */
    data class Curated(val list: PeopleList) : FeedScope

    /** Everything on the relays, spam included. Opt-in, never the default. */
    data object Global : FeedScope

    /** EPUB / PDF / text files imported onto this device. Never fetched. */
    data object OnDevice : FeedScope

}

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val continueArticles: List<Pair<Book, ReadingProgress>> = emptyList(),
    val continueBooks: List<Pair<Book, ReadingProgress>> = emptyList(),
    val favorites: Set<Coordinate> = emptySet(),
    val authors: Map<String, Profile> = emptyMap(),
    val query: String = "",
    val isRefreshing: Boolean = false,
    val scope: FeedScope = FeedScope.Following,
    val availableLists: List<PeopleList> = emptyList(),
    val isLoggedIn: Boolean = false,
    /** True when Following is selected but the user follows nobody yet. */
    val followsNobody: Boolean = false,
    val acquaintancesEmpty: Boolean = false,
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val progressRepository: ProgressRepository,
    private val favoriteRepository: FavoriteRepository,
    private val profileRepository: ProfileRepository,
    private val followRepository: FollowRepository,
    private val signerManager: SignerManager,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(false)
    private val scope = MutableStateFlow<FeedScope>(FeedScope.Following)

    /** Which pubkeys the current scope allows; empty means unrestricted. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scopedAuthors = combine(
        scope,
        followRepository.follows,
        followRepository.acquaintances,
    ) { active, follows, acquaintances ->
        when (active) {
            FeedScope.Global, FeedScope.OnDevice -> emptyList<String>()
            FeedScope.Following -> follows
            FeedScope.Acquaintances -> acquaintances
            is FeedScope.Curated -> active.list.pubkeys
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val books = combine(query, scopedAuthors, scope) { text, authors, active ->
        Triple(text, authors, active)
    }.flatMapLatest { (text, authors, active) ->
        val kinds = kindsFor(active)
        when {
            active == FeedScope.OnDevice ->
                if (text.isBlank()) libraryRepository.observeOnDevice()
                else libraryRepository.searchOnDevice(text)

            authors.isEmpty() && active != FeedScope.Global ->
                libraryRepository.observeLibrary(listOf(NO_AUTHOR), kinds)

            text.isBlank() -> libraryRepository.observeLibrary(authors, kinds)
            else -> libraryRepository.search(text, authors, kinds)
        }
    }

    private val history = combine(
        libraryRepository.observeLibrary(),
        progressRepository.observeContinueReading(),
    ) { allBooks, progress ->
        val pairs = resumable(allBooks, progress)
        pairs.filter { it.first.isLongFormArticle } to pairs.filter { !it.first.isLongFormArticle }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        books,
        history,
        favoriteRepository.observeAll(),
        combine(query, refreshing, scope) { q, r, s -> Triple(q, r, s) },
        combine(
            followRepository.lists,
            followRepository.follows,
            followRepository.acquaintances,
            signerManager.session,
        ) { lists, follows, acquaintances, session ->
            arrayOf(lists, follows, acquaintances, session.isLoggedIn)
        },
        profileRepository.profiles,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val books = values[0] as List<Book>
        @Suppress("UNCHECKED_CAST")
        val history = values[1] as Pair<List<Pair<Book, ReadingProgress>>, List<Pair<Book, ReadingProgress>>>
        @Suppress("UNCHECKED_CAST")
        val favorites = values[2] as List<Pair<Coordinate, Boolean>>
        @Suppress("UNCHECKED_CAST")
        val view = values[3] as Triple<String, Boolean, FeedScope>
        @Suppress("UNCHECKED_CAST")
        val social = values[4] as Array<*>
        @Suppress("UNCHECKED_CAST")
        val authors = values[5] as Map<String, Profile>

        val (text, isRefreshing, activeScope) = view
        @Suppress("UNCHECKED_CAST")
        val lists = social[0] as List<PeopleList>
        @Suppress("UNCHECKED_CAST")
        val follows = social[1] as List<String>
        @Suppress("UNCHECKED_CAST")
        val acquaintances = social[2] as List<String>
        val loggedIn = social[3] as Boolean

        LibraryUiState(
            books = books,
            continueArticles = history.first,
            continueBooks = history.second,
            favorites = favorites.map { it.first }.toSet(),
            authors = authors,
            query = text,
            isRefreshing = isRefreshing,
            scope = activeScope,
            availableLists = lists,
            isLoggedIn = loggedIn,
            followsNobody = activeScope == FeedScope.Following && follows.isEmpty(),
            acquaintancesEmpty = activeScope == FeedScope.Acquaintances && acquaintances.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        observeIdentity()
        resolveAuthors()
    }

    /**
     * Following is the home feed, signed in or not. A curated list belongs to
     * one identity, so logging out drops it back to Following. Global is never
     * forced — the user has to pick it.
     */
    private fun observeIdentity() {
        viewModelScope.launch {
            signerManager.session.collect { session ->
                if (!session.isLoggedIn) {
                    followRepository.clear()
                    if (scope.value is FeedScope.Curated) scope.value = FeedScope.Following
                } else {
                    followRepository.refresh()
                }
                refresh()
            }
        }
    }

    private fun resolveAuthors() {
        viewModelScope.launch {
            books.collect { visible ->
                val pubkeys = visible.filterNot { it.imported }.map(Book::authorPubkey)
                profileRepository.prefetch(pubkeys)
            }
        }
    }

    fun dismissHistory(coordinate: Coordinate) {
        viewModelScope.launch { progressRepository.forget(coordinate) }
    }

    fun clearArticleHistory() {
        viewModelScope.launch {
            progressRepository.forgetWhere { it.kind == Kinds.LONG_FORM }
        }
    }

    fun clearBookHistory() {
        viewModelScope.launch {
            progressRepository.forgetWhere { it.kind != Kinds.LONG_FORM }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setScope(value: FeedScope) {
        scope.value = value
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val active = scope.value
        if (active == FeedScope.OnDevice) return
        refreshing.value = true
        try {
            val authors = when (active) {
                FeedScope.Global, FeedScope.OnDevice -> emptyList()
                FeedScope.Following -> followRepository.follows.value
                FeedScope.Acquaintances -> followRepository.acquaintances.value
                is FeedScope.Curated -> active.list.pubkeys
            }
            if (authors.isNotEmpty() || active == FeedScope.Global) {
                libraryRepository.refreshLibrary(authors, kindsFor(active))
            }
        } finally {
            refreshing.value = false
        }
    }

    private fun kindsFor(active: FeedScope): List<Int>? = when (active) {
        FeedScope.Following, FeedScope.Acquaintances -> listOf(Kinds.LONG_FORM)
        FeedScope.Global, FeedScope.OnDevice, is FeedScope.Curated -> null
    }

    private fun resumable(
        books: List<Book>,
        progress: List<ReadingProgress>,
    ): List<Pair<Book, ReadingProgress>> {
        val byCoordinate = books.associateBy { it.coordinate }
        return progress.mapNotNull { entry -> byCoordinate[entry.bookCoordinate]?.let { it to entry } }
    }

    private companion object {
        /** A pubkey no event can have, so the query returns nothing. */
        const val NO_AUTHOR = "-"
    }
}
