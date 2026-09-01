package com.athena.reader.data.repository

import com.athena.reader.data.local.BookDao
import com.athena.reader.data.local.SectionDao
import com.athena.reader.data.local.toDomain
import com.athena.reader.data.local.toEntity
import com.athena.reader.data.nostr.EventMappers
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.Section
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Filter
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.relay.RelayPool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Books, offline-first: the UI always reads the cache, and relay traffic only
 * ever writes into it. That keeps the reader responsive on a bad connection and
 * makes "what is on screen" independent of "what a relay just decided to send".
 */
class LibraryRepository constructor(
    private val relayPool: RelayPool,
    private val bookDao: BookDao,
    private val sectionDao: SectionDao,
) {
    /** [authors] empty means "everyone in the cache" — the global view. */
    fun observeLibrary(
        authors: List<String> = emptyList(),
        kinds: List<Int>? = null,
    ): Flow<List<Book>> {
        val rows = if (authors.isEmpty()) bookDao.observeAll() else bookDao.observeByAuthors(authors)
        return rows.map { list -> list.mapNotNull { it.toDomain() }.ofKinds(kinds) }
    }

    fun observeOnDevice(): Flow<List<Book>> =
        bookDao.observeImported().map { list -> list.mapNotNull { it.toDomain() } }

    fun searchOnDevice(query: String): Flow<List<Book>> =
        bookDao.searchImported(query).map { list -> list.mapNotNull { it.toDomain() } }

    fun search(
        query: String,
        authors: List<String> = emptyList(),
        kinds: List<Int>? = null,
    ): Flow<List<Book>> {
        val rows = if (authors.isEmpty()) {
            bookDao.search(query)
        } else {
            bookDao.searchByAuthors(authors, query)
        }
        return rows.map { list -> list.mapNotNull { it.toDomain() }.ofKinds(kinds) }
    }

    fun observeBook(coordinate: Coordinate): Flow<Book?> =
        bookDao.observe(coordinate.asString()).map { it?.toDomain() }

    fun observeSections(coordinate: Coordinate): Flow<List<Section>> =
        sectionDao.observeForBook(coordinate.asString()).map { rows -> rows.mapNotNull { it.toDomain() } }

    /**
     * Pulls publications by [authors] into the cache, or from everyone when
     * [authors] is empty.
     *
     * The caller decides the scope, because "everyone" is a genuinely different
     * product: an unfiltered kind 30023 feed on nostr is largely spam, and the
     * app defaults to the user's own social graph instead.
     */
    suspend fun refreshLibrary(
        authors: List<String> = emptyList(),
        kinds: List<Int>? = null,
        limit: Int = 60,
    ): Int {
        val filter = if (authors.isEmpty()) {
            Filter.globalFeed(limit)
        } else {
            Filter.libraryOf(
                authors = authors.take(com.athena.reader.platform.Limits.MAX_FOLLOW_AUTHORS),
                kinds = kinds ?: Kinds.READABLE_ROOTS,
                limit = limit,
            )
        }
        val books = relayPool.fetch(listOf(filter)).mapNotNull(EventMappers::toBook)
        if (books.isNotEmpty()) bookDao.upsert(books.map { it.toEntity() })
        return books.size
    }

    /** Fetches one book by coordinate — the deep-link and "open from favorites" path. */
    suspend fun fetchBook(coordinate: Coordinate): Book? {
        val event = relayPool.fetch(listOf(Filter.byCoordinate(coordinate)))
            .maxByOrNull { it.createdAt }
            ?: return null
        val book = EventMappers.toBook(event) ?: return null
        bookDao.upsert(listOf(book.toEntity()))
        return book
    }

    /**
     * Loads a book's text. Cache first: an EPUB just imported is already on
     * disk, and waiting on relays would show a blank reader. Relays fill gaps
     * and refresh; they must not hide local chapters.
     */
    suspend fun fetchSections(book: Book): List<Section> {
        val cached = sectionDao.getForBook(book.coordinate.asString()).mapNotNull { it.toDomain() }
        if (book.isSinglePart) {
            if (cached.isNotEmpty()) return cached
            return inlineSection(book)
        }
        val expected = book.sectionRefs.size
        if (cached.size >= expected && expected > 0) return cached

        val remote = fetchSectionsFromRelays(book)
        if (remote.isNotEmpty()) return remote
        if (cached.isNotEmpty()) return cached
        return inlineSection(book)
    }

    private suspend fun fetchSectionsFromRelays(book: Book): List<Section> {
        val byAuthor = book.sectionRefs.groupBy(Coordinate::pubkey)
        val filters = byAuthor.map { (author, refs) ->
            Filter.sections(
                author,
                refs.map(Coordinate::identifier).take(com.athena.reader.platform.Limits.MAX_SECTION_REFS),
            )
        }
        val events = relayPool.fetch(filters)
            .filter { it.kind == Kinds.PUBLICATION_CONTENT }
            .groupBy { it.coordinate()?.asString() }
            .mapNotNull { (_, versions) -> versions.maxByOrNull { it.createdAt } }
            .associateBy { it.coordinate()?.asString() }

        val ordered = book.sectionRefs.mapIndexedNotNull { index, ref ->
            events[ref.asString()]?.let { EventMappers.toSection(it, index) }
        }
        if (ordered.isNotEmpty()) sectionDao.upsert(ordered.map { it.toEntity(book.coordinate) })
        return ordered
    }

    private suspend fun inlineSection(book: Book): List<Section> {
        val content = book.inlineContent ?: return emptyList()
        val section = Section(book.coordinate, book.title, content, index = 0)
        sectionDao.upsert(listOf(section.toEntity(book.coordinate)))
        return listOf(section)
    }
}

private fun List<Book>.ofKinds(kinds: List<Int>?): List<Book> =
    if (kinds == null) this else filter { it.coordinate.kind in kinds }
