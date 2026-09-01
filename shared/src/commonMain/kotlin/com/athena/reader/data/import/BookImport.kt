package com.athena.reader.data.importer

/** A chapter as it came out of a file, before the user has edited anything. */
data class ImportedSection(val title: String, val content: String)

/**
 * A book parsed from a file and not yet published.
 *
 * Metadata is separated from content because the point of the import screen is
 * that the user *fixes* the metadata: files carry titles like "pg1342.txt", and
 * a library is only as good as what you can search in it.
 */
data class ImportedBook(
    val title: String,
    val author: String,
    val summary: String = "",
    val language: String = "",
    val topics: List<String> = emptyList(),
    val sections: List<ImportedSection>,
    val sourceFileName: String = "",
    /** Stable `d` tag, set once when the book is shelved so Read and Publish share it. */
    val localSlug: String? = null,
) {
    val wordCount: Int get() = sections.sumOf { it.content.split(WHITESPACE).size }

    val isPublishable: Boolean
        get() = title.isNotBlank() && sections.isNotEmpty() &&
            sections.any { it.content.isNotBlank() }

    /**
     * Relays reject oversized events. A chapter that is still one blob after
     * import is split on paragraph boundaries so a novel-length PDF does not
     * vanish into a NOTICE.
     */
    fun fitRelayLimits(maxChars: Int = RELAY_SECTION_CHARS): ImportedBook =
        copy(sections = sections.flatMap { it.splitToFit(maxChars) })

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * Comfortably under the 64 KiB event cap most relays enforce, and under
 * NIP-44's 65 535-byte plaintext limit once the chapter is nested inside a
 * NIP-46 `sign_event` request (the JSON is escaped twice).
 */
const val RELAY_SECTION_CHARS = 12_000

fun ImportedSection.splitToFit(maxChars: Int): List<ImportedSection> {
    if (content.length <= maxChars) return listOf(this)

    val parts = mutableListOf<ImportedSection>()
    val buffer = StringBuilder()
    var index = 1

    fun flush() {
        val chunk = buffer.toString().trim()
        if (chunk.isEmpty()) return
        val label = if (index == 1) title else "$title ($index)"
        parts += ImportedSection(label, chunk)
        buffer.clear()
        index++
    }

    for (paragraph in content.split("\n\n")) {
        when {
            paragraph.length > maxChars -> {
                flush()
                paragraph.chunked(maxChars).forEach { piece ->
                    val label = if (index == 1) title else "$title ($index)"
                    parts += ImportedSection(label, piece)
                    index++
                }
            }
            buffer.isNotEmpty() && buffer.length + paragraph.length + 2 > maxChars -> {
                flush()
                buffer.append(paragraph)
            }
            else -> {
                if (buffer.isNotEmpty()) buffer.append("\n\n")
                buffer.append(paragraph)
            }
        }
    }
    flush()
    return parts.ifEmpty { listOf(this) }
}

/** How an imported book should reach the relays. */
enum class PublishVisibility {
    /**
     * Ordinary NKBIP-01: readable by every nostr client, contributes to the
     * commons. The right choice for public-domain work.
     */
    Public,

    /**
     * NIP-44 self-encrypted at blinded addresses: a personal copy, readable
     * only by this user, on every device they sign in from.
     *
     * This exists for a concrete reason. Most books someone owns cannot legally
     * be republished, and an app that only offered "publish to the world" would
     * be quietly inviting people to infringe copyright. This is the shelf for
     * everything else.
     */
    Private,
}

/** Anything that can turn bytes into a book. */
interface BookImporter {
    /** Lower-cased extensions this importer claims, without the dot. */
    val extensions: Set<String>

    fun canHandle(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in extensions

    /** Null when the bytes turn out not to be what the extension promised. */
    fun import(fileName: String, bytes: ByteArray): ImportedBook?
}
