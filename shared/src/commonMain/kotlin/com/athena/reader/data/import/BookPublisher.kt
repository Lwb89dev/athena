package com.athena.reader.data.importer

import com.athena.reader.data.local.BookDao
import com.athena.reader.data.local.SectionDao
import com.athena.reader.data.local.toEntity
import com.athena.reader.data.sync.EncryptedSync
import com.athena.reader.domain.model.Book
import com.athena.reader.domain.model.Section
import com.athena.reader.nostr.crypto.BlindedPath
import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Kinds
import com.athena.reader.nostr.model.UnsignedEvent
import com.athena.reader.nostr.relay.RelayPool
import com.athena.reader.nostr.signer.SignerManager
import com.athena.reader.platform.Log
import com.athena.reader.platform.nowSeconds
import com.athena.reader.platform.randomBytes
import com.athena.reader.nostr.crypto.toHex

/** What happened, in terms the import screen can show. */
sealed interface PublishResult {
    data class Success(val coordinate: Coordinate, val sectionCount: Int) : PublishResult
    data class Failure(val reason: String) : PublishResult
}

/**
 * Turns an imported book into nostr events.
 *
 * Public books become ordinary NKBIP-01: one kind 30041 per chapter plus a
 * kind 30040 index tying them together in reading order. Any nostr client can
 * then open them — that is the whole point of the gateway.
 *
 * Private books take the [EncryptedSync] path instead: chapters and index are
 * NIP-44 self-encrypted at blinded addresses, so they reach the user's other
 * devices and nobody else, including the relay operator.
 *
 * The chapter events go out *before* the index, always. The index references
 * them by coordinate, so publishing it first would create a book that is
 * briefly readable and briefly broken.
 */
class BookPublisher(
    private val relayPool: RelayPool,
    private val signerManager: SignerManager,
    private val encryptedSync: EncryptedSync,
    private val bookDao: BookDao,
    private val sectionDao: SectionDao,
) {
    suspend fun publish(
        book: ImportedBook,
        visibility: PublishVisibility,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): PublishResult {
        val signer = signerManager.current.value
        val pubkey = signer.pubkeyHex
            ?: return PublishResult.Failure("Sign in before publishing a book.")
        if (!book.isPublishable) return PublishResult.Failure("This book has no readable text.")

        val slug = book.localSlug ?: slugFor(book)
        return when (visibility) {
            PublishVisibility.Public -> publishPublic(book, pubkey, slug, onProgress)
            PublishVisibility.Private -> publishPrivate(book, pubkey, slug, onProgress)
        }
    }

    /**
     * Puts the whole book in the local library so the reader can open it
     * immediately — publish to relays is a separate, slower step.
     */
    suspend fun shelveLocally(book: ImportedBook, pubkey: String): Coordinate {
        val slug = book.localSlug ?: slugFor(book)
        val coordinate = Coordinate(Kinds.PUBLICATION_INDEX, pubkey, slug)
        val sectionCoordinates = book.sections.indices.map { index ->
            Coordinate(Kinds.PUBLICATION_CONTENT, pubkey, "$slug-${index + 1}")
        }
        cacheLocally(book, coordinate, sectionCoordinates)
        return coordinate
    }

    private suspend fun publishPublic(
        book: ImportedBook,
        pubkey: String,
        slug: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): PublishResult {
        val signer = signerManager.current.value
        val sectionCoordinates = mutableListOf<Coordinate>()
        val total = book.sections.size + 1

        book.sections.forEachIndexed { index, section ->
            onProgress(index, total)
            val identifier = "$slug-${index + 1}"
            val unsigned = UnsignedEvent(
                kind = Kinds.PUBLICATION_CONTENT,
                tags = listOf(
                    listOf("d", identifier),
                    listOf("title", section.title),
                ),
                content = section.content,
            )
            val signed = signer.sign(unsigned).getOrElse { error ->
                return PublishResult.Failure(
                    "Could not sign chapter ${index + 1} (${section.title}): " +
                        (error.message ?: "the signer refused."),
                )
            }
            relayPool.publish(signed)
            sectionCoordinates += Coordinate(Kinds.PUBLICATION_CONTENT, pubkey, identifier)
        }

        onProgress(book.sections.size, total)
        val index = UnsignedEvent(
            kind = Kinds.PUBLICATION_INDEX,
            tags = indexTags(book, slug, sectionCoordinates),
            content = "",
        )
        val signedIndex = signer.sign(index).getOrElse { error ->
            return PublishResult.Failure(
                "Chapters went out but the index was not signed: " +
                    (error.message ?: "the signer refused."),
            )
        }
        relayPool.publish(signedIndex)
        onProgress(total, total)

        val coordinate = Coordinate(Kinds.PUBLICATION_INDEX, pubkey, slug)
        cacheLocally(book, coordinate, sectionCoordinates)
        return PublishResult.Success(coordinate, book.sections.size)
    }

    /**
     * A private book is the same shape, but every address is blinded and every
     * chapter is ciphertext. Kept as separate events rather than one blob so a
     * long book does not blow past NIP-44's 65 535-byte plaintext limit.
     */
    private suspend fun publishPrivate(
        book: ImportedBook,
        pubkey: String,
        slug: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): PublishResult {
        val secret = encryptedSync.secret()
            ?: return PublishResult.Failure(
                "Turn on private sync in Settings first — a private book needs somewhere " +
                    "only you can read it from.",
            )

        val total = book.sections.size + 1
        book.sections.forEachIndexed { index, section ->
            onProgress(index, total)
            val slot = BlindedPath.derive(secret, NAMESPACE_SECTION, "$slug-${index + 1}")
            val payload = "${section.title}\n\n${section.content}"
            if (!encryptedSync.put(slot, payload)) {
                return PublishResult.Failure("Encrypted upload failed at chapter ${index + 1}.")
            }
        }

        val manifest = buildString {
            appendLine(book.title)
            appendLine(book.author)
            appendLine(book.sections.size.toString())
            book.sections.forEach { appendLine(it.title) }
        }
        onProgress(book.sections.size, total)
        val indexSlot = BlindedPath.derive(secret, NAMESPACE_INDEX, slug)
        if (!encryptedSync.put(indexSlot, manifest)) {
            return PublishResult.Failure("Chapters were stored but the index was not.")
        }
        onProgress(total, total)

        val coordinate = Coordinate(Kinds.APP_DATA, pubkey, indexSlot)
        val localChapters = book.sections.indices.map { index ->
            Coordinate(Kinds.PUBLICATION_CONTENT, pubkey, "$slug-${index + 1}")
        }
        cacheLocally(book, coordinate, localChapters)
        return PublishResult.Success(coordinate, book.sections.size)
    }

    private fun indexTags(
        book: ImportedBook,
        slug: String,
        sections: List<Coordinate>,
    ): List<List<String>> = buildList {
        add(listOf("d", slug))
        add(listOf("title", book.title))
        if (book.author.isNotBlank()) add(listOf("author", book.author))
        if (book.summary.isNotBlank()) add(listOf("summary", book.summary))
        if (book.language.isNotBlank()) add(listOf("l", book.language))
        add(listOf("published_on", nowSeconds().toString()))
        book.topics.filter(String::isNotBlank).forEach { add(listOf("t", it.lowercase())) }
        sections.forEach { add(it.asTag()) }
    }

    /** So the book appears in the library immediately, without a relay round-trip. */
    private suspend fun cacheLocally(
        book: ImportedBook,
        coordinate: Coordinate,
        sectionCoordinates: List<Coordinate>,
    ) {
        val domain = Book(
            coordinate = coordinate,
            title = book.title,
            authorPubkey = coordinate.pubkey,
            authorName = book.author.ifBlank { null },
            summary = book.summary.ifBlank { null },
            publishedAt = nowSeconds(),
            topics = book.topics,
            language = book.language.ifBlank { null },
            sectionRefs = sectionCoordinates,
            inlineContent = if (sectionCoordinates.size <= 1) book.sections.firstOrNull()?.content else null,
            imported = true,
        )
        bookDao.upsert(listOf(domain.toEntity()))

        val sections = book.sections.mapIndexed { index, section ->
            Section(
                coordinate = sectionCoordinates.getOrNull(index) ?: coordinate,
                title = section.title,
                content = section.content,
                index = index,
            )
        }
        sectionDao.upsert(sections.map { it.toEntity(coordinate) })
        Log.d(TAG, "cached '${book.title}' with ${sections.size} sections")
    }

    /**
     * A readable, unique `d` tag. Readable because a public book's address is
     * public anyway and a legible one is friendlier to link; unique because two
     * uploads of the same title must not overwrite each other — `d` tags are
     * replaceable-event keys, not names.
     */
    internal fun slugFor(book: ImportedBook): String {
        val base = book.title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(60)
            .ifBlank { "book" }
        return "$base-${randomBytes(4).toHex()}"
    }

    internal companion object {
        const val TAG = "BookPublisher"
        const val NAMESPACE_SECTION = "book-section"
        const val NAMESPACE_INDEX = "book-index"

        /** Identity for books read on this device without a login. 64 hex zeros. */
        const val LOCAL_SHELF_PUBKEY = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
