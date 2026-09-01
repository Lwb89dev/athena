package com.athena.reader.data.importer

import com.athena.reader.platform.extractPdf

/**
 * PDF via the platform extractor (PDFBox on desktop, pdfbox-android on the
 * phone). We publish the *text*, not the file: NKBIP-01 is a sequence of
 * chapters, and a binary blob on a relay would be both oversized and unreadable
 * to every other client.
 *
 * Scanned PDFs with no text layer come back null — there is nothing to put on
 * a relay that a reader could highlight.
 */
class PdfImporter : BookImporter {

    override val extensions = setOf("pdf")

    override fun import(fileName: String, bytes: ByteArray): ImportedBook? {
        val extracted = extractPdf(bytes) ?: return null
        val text = extracted.text.trim()
        if (text.isBlank()) return null

        val fallbackTitle = fileName.substringBeforeLast('.')
            .replace(Regex("[_-]+"), " ")
            .trim()
            .ifBlank { "Untitled" }

        val headingSections = splitOnHeadings(text)
        val sections = when {
            headingSections.size >= 2 -> headingSections
            else -> listOf(ImportedSection(extracted.title ?: fallbackTitle, text))
        }

        return ImportedBook(
            title = extracted.title?.takeIf(String::isNotBlank) ?: fallbackTitle,
            author = extracted.author.orEmpty(),
            sections = sections,
            sourceFileName = fileName,
        )
    }

    /**
     * Same heading shapes [TextImporter] recognises, so a PDF that started life
     * as a typeset book still splits into chapters instead of one giant event.
     */
    private fun splitOnHeadings(text: String): List<ImportedSection> {
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

    private fun String.isHeading(): Boolean {
        val line = trim()
        if (line.isEmpty() || line.length > 120) return false
        val marked = Regex("^(#{1,3}|={1,3})\\s+\\S").containsMatchIn(line)
        val chapter = Regex("^(chapter|capitolo|part|book)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(line)
        return marked || chapter
    }
}
