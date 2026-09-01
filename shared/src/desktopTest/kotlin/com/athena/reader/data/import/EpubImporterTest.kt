package com.athena.reader.data.importer

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Built as a real zip rather than checked in as a fixture, so the test states
 * exactly which EPUB structure it relies on. Lives in the JVM source set
 * because assembling a zip needs a platform API.
 */
class EpubImporterTest {

    private val importer = EpubImporter()

    @Test
    fun `reads metadata and follows the spine, not the file names`() {
        // The chapter files are deliberately named out of order: an importer
        // that sorted by name would put the book back to front.
        val epub = buildEpub(
            opf = """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>The Republic</dc:title>
                    <dc:creator>Plato</dc:creator>
                    <dc:language>en</dc:language>
                    <dc:description>On justice and the ideal city.</dc:description>
                    <dc:subject>philosophy</dc:subject>
                    <dc:subject>classics</dc:subject>
                  </metadata>
                  <manifest>
                    <item id="c2" href="zzz.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c1" href="aaa.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent(),
            chapters = mapOf(
                "OEBPS/aaa.xhtml" to "<html><body><h1>Book One</h1><p>I went down yesterday.</p></body></html>",
                "OEBPS/zzz.xhtml" to "<html><body><h1>Book Two</h1><p>With these words I was thinking.</p></body></html>",
            ),
        )

        val book = importer.import("republic.epub", epub)!!

        assertEquals("The Republic", book.title)
        assertEquals("Plato", book.author)
        assertEquals("en", book.language)
        assertEquals("On justice and the ideal city.", book.summary)
        assertEquals(listOf("philosophy", "classics"), book.topics)
        assertEquals(listOf("Book One", "Book Two"), book.sections.map { it.title })
    }

    @Test
    fun `strips markup and decodes entities`() {
        val epub = buildEpub(
            opf = minimalOpf,
            chapters = mapOf(
                "OEBPS/aaa.xhtml" to
                    "<html><body><h1>One</h1><p>Caf&eacute;s &amp; books&hellip;</p>" +
                    "<script>alert('x')</script><p>Second line.</p></body></html>",
            ),
        )

        val text = importer.import("x.epub", epub)!!.sections.single().content

        assertTrue("&amp;" !in text && "<p>" !in text, "markup survived: $text")
        assertTrue("alert" !in text, "script contents leaked into the book")
        assertTrue("books…" in text, "entities were not decoded: $text")
        assertTrue("Second line." in text)
    }

    @Test
    fun `follows a percent-encoded href to the zip entry`() {
        val epub = buildEpub(
            opf = """
                <?xml version="1.0"?>
                <package version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
                  <manifest><item id="c1" href="a%20b.xhtml" media-type="application/xhtml+xml"/></manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent(),
            chapters = mapOf(
                "OEBPS/a b.xhtml" to "<html><body><h1>One</h1><p>Hello.</p></body></html>",
            ),
        )

        val book = importer.import("encoded.epub", epub)!!
        assertEquals("One", book.sections.single().title)
        assertTrue("Hello." in book.sections.single().content)
    }

    @Test
    fun `rejects something that is not a zip`() {
        assertNull(importer.import("fake.epub", "just text".encodeToByteArray()))
    }

    @Test
    fun `rejects a zip with no readable chapters`() {
        val epub = buildEpub(opf = minimalOpf, chapters = mapOf("OEBPS/aaa.xhtml" to "<html></html>"))
        assertNull(importer.import("empty.epub", epub))
    }

    private val minimalOpf = """
        <?xml version="1.0"?>
        <package version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
          <manifest><item id="c1" href="aaa.xhtml" media-type="application/xhtml+xml"/></manifest>
          <spine><itemref idref="c1"/></spine>
        </package>
    """.trimIndent()

    private fun buildEpub(opf: String, chapters: Map<String, String>): ByteArray {
        val container = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun write(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            write("mimetype", "application/epub+zip")
            write("META-INF/container.xml", container)
            write("OEBPS/content.opf", opf)
            chapters.forEach { (name, body) -> write(name, body) }
        }
        return out.toByteArray()
    }
}
