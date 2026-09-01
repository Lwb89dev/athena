package com.athena.reader.platform

/**
 * Hard caps so a malicious file, zip, relay or event cannot take the process
 * down or flood the network. The numbers are the product of "a real book still
 * fits" and "a zip bomb / event flood does not".
 */
object Limits {
    /** Largest import we will even open. Images-heavy EPUBs above this are refused. */
    const val MAX_FILE_BYTES = 32L * 1024 * 1024

    const val MAX_ZIP_ENTRIES = 300
    const val MAX_ZIP_ENTRY_BYTES = 2 * 1024 * 1024
    const val MAX_ZIP_TOTAL_BYTES = 32 * 1024 * 1024

    const val MAX_EVENT_CONTENT_CHARS = 24_000
    const val MAX_TITLE_CHARS = 500
    const val MAX_SUMMARY_CHARS = 4_000
    const val MAX_COORDINATE_D_CHARS = 256

    const val MAX_RELAYS = 12
    const val MAX_FETCH_EVENTS = 200
    const val MAX_FOLLOW_AUTHORS = 400
    const val MAX_SECTION_REFS = 400
    const val MAX_WS_FRAME_BYTES = 262_144L
}

/** Zip entries that would escape the archive if we ever wrote them to disk. */
fun isSafeArchivePath(name: String): Boolean {
    val path = name.replace('\\', '/')
    if (path.isBlank() || path.startsWith("/")) return false
    return path.split('/').none { it == ".." || it == "" }
}
