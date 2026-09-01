package com.athena.reader.data.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextImporterTest {

    private val importer = TextImporter()

    @Test
    fun `splits on markdown headings and keeps their order`() {
        val source = """
            # Meditations
            Author: Marcus Aurelius

            ## Book One
            From my grandfather Verus I learned good morals.

            ## Book Two
            Begin the morning by saying to thyself.
        """.trimIndent()

        val book = importer.import("meditations.md", source.encodeToByteArray())!!

        assertEquals("Meditations", book.title)
        assertEquals("Marcus Aurelius", book.author)
        assertEquals(listOf("Meditations", "Book One", "Book Two"), book.sections.map { it.title })
    }

    @Test
    fun `recognises bare CHAPTER lines, as plain-text books use`() {
        val source = """
            CHAPTER I
            It is a truth universally acknowledged.

            CHAPTER II
            Mr Bennet was among the earliest.
        """.trimIndent()

        val book = importer.import("pg1342.txt", source.encodeToByteArray())!!
        assertEquals(2, book.sections.size)
    }

    @Test
    fun `falls back to the file name when there is no heading`() {
        val book = importer.import(
            "the-trial_kafka.txt",
            "Someone must have slandered Josef K.".encodeToByteArray(),
        )!!

        assertEquals("the trial kafka", book.title)
        assertEquals(1, book.sections.size)
    }

    @Test
    fun `refuses an empty file instead of publishing nothing`() {
        assertNull(importer.import("empty.txt", "   \n\n  ".encodeToByteArray()))
    }

    @Test
    fun `claims only the extensions it can read`() {
        assertTrue(importer.canHandle("book.EPUB".lowercase().replace("epub", "md")))
        assertTrue(!importer.canHandle("book.epub"))
        assertTrue(!importer.canHandle("book.pdf"))
    }
}

class ImportedBookTest {

    @Test
    fun `is not publishable without a title`() {
        val book = ImportedBook(
            title = "  ",
            author = "someone",
            sections = listOf(ImportedSection("One", "text")),
        )
        assertTrue(!book.isPublishable)
    }

    @Test
    fun `is not publishable when every section is empty`() {
        val book = ImportedBook(
            title = "Title",
            author = "",
            sections = listOf(ImportedSection("One", "   ")),
        )
        assertTrue(!book.isPublishable)
    }

    @Test
    fun `counts words across sections`() {
        val book = ImportedBook(
            title = "Title",
            author = "",
            sections = listOf(
                ImportedSection("One", "three words here"),
                ImportedSection("Two", "two more"),
            ),
        )
        assertEquals(5, book.wordCount)
    }

    @Test
    fun `splits a chapter that would blow the relay size cap`() {
        val paragraph = "word ".repeat(100).trim()
        val content = (1..50).joinToString("\n\n") { paragraph }
        val split = ImportedSection("Long", content).splitToFit(maxChars = 800)

        assertTrue(split.size > 1)
        assertTrue(split.all { it.content.length <= 800 })
        assertEquals("Long", split.first().title)
        assertEquals("Long (2)", split[1].title)
        assertEquals(content.replace(Regex("\\s+"), " ").trim(),
            split.joinToString(" ") { it.content.replace(Regex("\\s+"), " ").trim() }.trim())
    }

    @Test
    fun `leaves a short chapter alone`() {
        val section = ImportedSection("Short", "hello")
        assertEquals(listOf(section), section.splitToFit(20_000))
    }
}
