package com.athena.reader.data.importer

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PdfImporterTest {

    private val importer = PdfImporter()

    @Test
    fun `reads title author and body from a real PDF`() {
        val pdf = buildPdf(
            title = "On Liberty",
            author = "John Stuart Mill",
            lines = listOf("The subject of this essay is civil liberty."),
        )

        val book = importer.import("on-liberty.pdf", pdf)!!

        assertEquals("On Liberty", book.title)
        assertEquals("John Stuart Mill", book.author)
        assertTrue(book.sections.single().content.contains("civil liberty"))
    }

    @Test
    fun `splits on CHAPTER headings the way a typeset book would`() {
        val pdf = buildPdf(
            title = "Pride and Prejudice",
            author = "Jane Austen",
            lines = listOf(
                "CHAPTER I",
                "It is a truth universally acknowledged.",
                "CHAPTER II",
                "Mr Bennet was among the earliest.",
            ),
        )

        val book = importer.import("pg1342.pdf", pdf)!!
        assertEquals(listOf("CHAPTER I", "CHAPTER II"), book.sections.map { it.title })
    }

    @Test
    fun `rejects bytes that are not a PDF`() {
        assertNull(importer.import("fake.pdf", "not a pdf".encodeToByteArray()))
    }

    @Test
    fun `claims the pdf extension`() {
        assertTrue(importer.canHandle("book.PDF"))
        assertTrue(!importer.canHandle("book.epub"))
    }

    private fun buildPdf(title: String, author: String, lines: List<String>): ByteArray {
        PDDocument().use { document ->
            document.documentInformation.title = title
            document.documentInformation.author = author
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(72f, 720f)
                stream.setLeading(16f)
                lines.forEach { line ->
                    stream.showText(line)
                    stream.newLine()
                }
                stream.endText()
            }
            val out = ByteArrayOutputStream()
            document.save(out)
            return out.toByteArray()
        }
    }
}
