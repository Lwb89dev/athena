package com.athena.reader.nostr.model

/**
 * Every event kind Athena reads or writes, with the spec it comes from.
 * Nothing here is invented: the whole app is expressible with existing NIPs.
 */
object Kinds {
    /** NIP-01 profile metadata. */
    const val METADATA = 0

    /** NIP-02 contact list — who the user follows. */
    const val CONTACTS = 3

    /** NIP-51 follow set: a named, curated group of people. */
    const val FOLLOW_SET = 30_000

    /** NIP-09 deletion request. */
    const val DELETION = 5

    /** NIP-51 generic bookmark list (kept for interop; we write [BOOKMARK_SET]). */
    const val BOOKMARKS = 10_003

    /** NIP-65 relay list metadata. */
    const val RELAY_LIST = 10_002

    /** NIP-46 remote signer (bunker) request/response. */
    const val NOSTR_CONNECT = 24_133

    /** NIP-84 highlight. Content is the highlighted passage verbatim. */
    const val HIGHLIGHT = 9_802

    /** NIP-51 bookmark *set* — addressable, so we can namespace ours with a `d` tag. */
    const val BOOKMARK_SET = 30_003

    /** NIP-23 long-form article. Treated as a single-section book. */
    const val LONG_FORM = 30_023

    /** NIP-78 arbitrary app data. Carries encrypted reading progress. */
    const val APP_DATA = 30_078

    /** NKBIP-01 publication index: the book's table of contents. */
    const val PUBLICATION_INDEX = 30_040

    /** NKBIP-01 publication content: one section ("zettel") of a book. */
    const val PUBLICATION_CONTENT = 30_041

    /** Kinds that can be the root of something readable in the library. */
    val READABLE_ROOTS = listOf(PUBLICATION_INDEX, LONG_FORM)
}

/**
 * Readable `d`-tag namespaces.
 *
 * Only *public* data may use one. Everything private is addressed through
 * `BlindedPath` instead, because a readable `d` tag is queryable and therefore
 * tells a relay what the slot is about even when the content is sealed.
 */
object AppNamespace {
    /** Public favourites: meant to be found by other clients. */
    const val FAVORITES_SET = "project-athena-favorites"
}
