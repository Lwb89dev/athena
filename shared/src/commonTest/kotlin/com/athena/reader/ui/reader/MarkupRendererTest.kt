package com.athena.reader.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkupRendererTest {

    @Test
    fun `strips asciidoc heading markers`() {
        val rendered = MarkupRenderer.render("== Chapter One\nText.", Markup.AsciiDoc)
        assertEquals("Chapter One\nText.", rendered.text.text)
    }

    @Test
    fun `strips markdown heading markers`() {
        val rendered = MarkupRenderer.render("### Section\nText.", Markup.Markdown)
        assertEquals("Section\nText.", rendered.text.text)
    }

    @Test
    fun `strips paired emphasis but keeps a lone marker`() {
        assertEquals("bold text", MarkupRenderer.render("**bold** text", Markup.Markdown).text.text)
        assertEquals("italic", MarkupRenderer.render("_italic_", Markup.Markdown).text.text)

        // A stray marker is content, not markup: 2 * 3 must survive intact.
        assertEquals("2 * 3 = 6", MarkupRenderer.render("2 * 3 = 6", Markup.AsciiDoc).text.text)
    }

    @Test
    fun `turns list markers into bullets`() {
        val rendered = MarkupRenderer.render("* first\n* second", Markup.AsciiDoc)
        assertEquals("•  first\n•  second", rendered.text.text)
    }

    @Test
    fun `maps a source offset back to the same words after rendering`() {
        val source = "== Title\nThe **quick** brown fox."
        val rendered = MarkupRenderer.render(source, Markup.Markdown)

        val sourceStart = source.indexOf("quick")
        val sourceEnd = sourceStart + "quick".length

        val renderedStart = rendered.toRenderedOffset(sourceStart)
        val renderedEnd = rendered.toRenderedOffset(sourceEnd)

        assertEquals("quick", rendered.text.text.substring(renderedStart, renderedEnd))
    }

    @Test
    fun `maps a rendered selection back to the same source words`() {
        val source = "Some *emphasis* here."
        val rendered = MarkupRenderer.render(source, Markup.AsciiDoc)

        val renderedStart = rendered.text.text.indexOf("emphasis")
        val renderedEnd = renderedStart + "emphasis".length

        val sourceRange = rendered.toSourceRange(renderedStart, renderedEnd)!!

        assertEquals("emphasis", source.substring(sourceRange.first, sourceRange.last + 1))
    }

    @Test
    fun `survives unbalanced markers without losing text`() {
        // This is the input that would desynchronise a push/pop renderer.
        val source = "*unclosed bold and _unclosed italic\nnext line"
        val rendered = MarkupRenderer.render(source, Markup.AsciiDoc)

        assertTrue(rendered.text.text.contains("unclosed bold"))
        assertTrue(rendered.text.text.contains("next line"))
    }

    @Test
    fun `plain text is returned untouched with identity offsets`() {
        val source = "Nothing to see here. 2 * 3, _underscored_ nonsense."
        val rendered = MarkupRenderer.render(source, Markup.Plain)

        assertEquals(source, rendered.text.text)
        assertEquals(7, rendered.toRenderedOffset(7))
        assertEquals(7, rendered.toSourceOffset(7))
    }

    @Test
    fun `detects markup from the content and the kind`() {
        assertEquals(Markup.AsciiDoc, MarkupRenderer.detect("== Title", isAsciiDoc = true))
        assertEquals(Markup.Markdown, MarkupRenderer.detect("# Title\nbody", isAsciiDoc = false))
        assertEquals(Markup.Plain, MarkupRenderer.detect("just prose", isAsciiDoc = false))
    }

    @Test
    fun `offsets stay in range for an empty section`() {
        val rendered = MarkupRenderer.render("", Markup.Markdown)
        assertEquals("", rendered.text.text)
        assertEquals(0, rendered.toRenderedOffset(0))
        assertEquals(0, rendered.toSourceOffset(0))
    }
}
