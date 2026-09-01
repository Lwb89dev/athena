package com.athena.reader.nostr.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A NIP-01 REQ filter. Tag filters are kept in [tags] keyed by the bare letter
 * ("a", "e", "t") and rendered as "#a", "#e", "#t" on the wire.
 */
data class Filter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        ids?.let { put("ids", it.toJsonArray()) }
        authors?.let { put("authors", it.toJsonArray()) }
        kinds?.let { list -> put("kinds", buildJsonArray { list.forEach { add(it) } }) }
        tags.forEach { (letter, values) -> put("#$letter", values.toJsonArray()) }
        since?.let { put("since", it) }
        until?.let { put("until", it) }
        limit?.let { put("limit", it) }
        search?.let { put("search", it) }
    }

    private fun List<String>.toJsonArray() = buildJsonArray { forEach { add(it) } }

    companion object {
        /**
         * Books published by anyone, newest first.
         *
         * Deliberately not the default view: kind 30023 is wide open and a
         * large share of it is spam. See [libraryOf].
         */
        fun globalFeed(limit: Int = 60) = Filter(kinds = Kinds.READABLE_ROOTS, limit = limit)

        /** Books published by a specific set of people — the usable library. */
        fun libraryOf(authors: List<String>, kinds: List<Int> = Kinds.READABLE_ROOTS, limit: Int = 200) =
            Filter(authors = authors, kinds = kinds, limit = limit)

        /** Long-form articles (NIP-23) from a set of authors. */
        fun longFormOf(authors: List<String>, limit: Int = 80) = Filter(
            authors = authors,
            kinds = listOf(Kinds.LONG_FORM),
            limit = limit,
        )

        /** The sections of a book, fetched by their `d` tags. */
        fun sections(author: String, identifiers: List<String>) = Filter(
            authors = listOf(author),
            kinds = listOf(Kinds.PUBLICATION_CONTENT),
            tags = mapOf("d" to identifiers),
        )

        fun byCoordinate(coordinate: Coordinate) = Filter(
            authors = listOf(coordinate.pubkey),
            kinds = listOf(coordinate.kind),
            tags = mapOf("d" to listOf(coordinate.identifier)),
            limit = 1,
        )

        /** Public highlights authored by [pubkey] (NIP-84). */
        fun highlightsOf(pubkey: String, limit: Int = 200) =
            Filter(authors = listOf(pubkey), kinds = listOf(Kinds.HIGHLIGHT), limit = limit)

        /** Everyone's highlights on a given book — the "popular passages" view. */
        fun highlightsOn(coordinate: Coordinate, limit: Int = 200) = Filter(
            kinds = listOf(Kinds.HIGHLIGHT),
            tags = mapOf("a" to listOf(coordinate.asString())),
            limit = limit,
        )
    }
}
