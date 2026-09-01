package com.athena.reader.domain.model

import com.athena.reader.nostr.model.Coordinate
import com.athena.reader.nostr.model.Kinds
import kotlin.test.Test
import kotlin.test.assertTrue

class BookHistoryTest {

    @Test
    fun `nip-23 works are articles, publication indexes are books`() {
        val article = sample(Kinds.LONG_FORM)
        val volume = sample(Kinds.PUBLICATION_INDEX)
        assertTrue(article.isLongFormArticle)
        assertTrue(!volume.isLongFormArticle)
    }

    private fun sample(kind: Int) = Book(
        coordinate = Coordinate(kind, "a".repeat(64), "d"),
        title = "T",
        authorPubkey = "a".repeat(64),
    )
}
