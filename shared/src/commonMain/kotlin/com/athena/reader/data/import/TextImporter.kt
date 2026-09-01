package com.athena.reader.data.importer

/**
 * Plain text, Markdown and AsciiDoc.
 *
 * Chapters are found by looking for headings the file already has, rather than
 * cutting at a fixed length: a book split mid-sentence every 20 KB is worse
 * than one long section, and readers navigate by chapter.
 */
class TextImporter : BookImporter {

    override val extensions = setOf("txt", "md", "markdown", "adoc", "asciidoc", "text")

    override fun import(fileName: String, bytes: ByteArray): ImportedBook? {
        val text = bytes.decodeToString().replace("\r\n", "\n").trim()
        if (text.isBlank()) return null

        val sections = splitIntoSections(text)
        val fallbackTitle = fileName.substringBeforeLast('.').replace(Regex("[_-]+"), " ").trim()

        return ImportedBook(
            // A leading heading is almost always the real title; the file name
            // is the fallback, and usually something like "pg1342".
            title = detectedTitle(text) ?: fallbackTitle.ifBlank { "Untitled" },
            author = detectedAuthor(text).orEmpty(),
            sections = sections.ifEmpty { listOf(ImportedSection("Text", text)) },
            sourceFileName = fileName,
        )
    }

    private fun detectedTitle(text: String): String? {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val stripped = firstLine.trimStart('#', '=', ' ').trim()
        return stripped.takeIf { it.isNotBlank() && it.length <= 120 && firstLine != stripped }
    }

    /** Project Gutenberg and most plain-text books put the author on its own line. */
    private fun detectedAuthor(text: String): String? =
        text.lineSequence().take(20)
            .firstOrNull { it.trim().startsWith("Author:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()

    private fun splitIntoSections(text: String): List<ImportedSection> {
        val lines = text.lines()
        val headings = lines.withIndex().filter { (_, line) -> line.isHeading() }
        if (headings.size < 2) return emptyList()

        return headings.mapIndexed { index, (lineNumber, headingLine) ->
            val end = headings.getOrNull(index + 1)?.index ?: lines.size
            ImportedSection(
                title = headingLine.trimStart('#', '=', ' ').trim().ifBlank { "Section ${index + 1}" },
                content = lines.subList(lineNumber, end).joinToString("\n").trim(),
            )
        }.filter { it.content.isNotBlank() }
    }

    /**
     * Markdown `#`, AsciiDoc `=`, or a bare "CHAPTER ..." line — the three
     * shapes that actually appear in the books people have lying around.
     */
    private fun String.isHeading(): Boolean {
        val line = trim()
        if (line.isEmpty() || line.length > 120) return false

        val marked = Regex("^(#{1,3}|={1,3})\\s+\\S").containsMatchIn(line)
        val chapter = Regex("^(chapter|capitolo|part|book)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(line)
        return marked || chapter
    }
}
