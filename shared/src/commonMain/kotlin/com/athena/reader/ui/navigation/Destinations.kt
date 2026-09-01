package com.athena.reader.ui.navigation

import com.athena.reader.nostr.crypto.Nip19
import com.athena.reader.nostr.model.Coordinate

/**
 * Routes carry an `naddr`, not a database id: it is the same identifier a book
 * has outside the app, so every screen is shareable as a `nostr:` link for free.
 */
object Destinations {
    const val LIBRARY = "library"
    const val READ = "read"
    const val HIGHLIGHTS = "highlights"
    const val IMPORT = "import"
    const val SETTINGS = "settings"

    const val BOOK_ARG = "naddr"
    const val BOOK = "book/{$BOOK_ARG}"
    const val READER = "reader/{$BOOK_ARG}"

    fun book(coordinate: Coordinate): String = "book/${Nip19.encodeNaddr(coordinate)}"
    fun reader(coordinate: Coordinate): String = "reader/${Nip19.encodeNaddr(coordinate)}"
}
