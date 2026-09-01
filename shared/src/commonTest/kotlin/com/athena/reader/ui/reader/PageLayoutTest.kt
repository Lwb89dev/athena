package com.athena.reader.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageLayoutTest {

    @Test
    fun `snap does not split a word when a space sits in the second half`() {
        val text = "hello world again"
        val end = snapPageEnd(text, 0, 8)
        assertEquals(6, end)
        assertEquals("hello ", text.substring(0, end))
    }

    @Test
    fun `snap keeps the raw end when no break is close`() {
        val text = "abcdefghij"
        assertEquals(6, snapPageEnd(text, 0, 6))
    }

    @Test
    fun `pageIndexFor finds the page that owns an offset`() {
        val pages = listOf(
            ReaderPage(0, 0, 10),
            ReaderPage(0, 10, 20),
            ReaderPage(1, 0, 8),
        )
        assertEquals(1, pageIndexFor(pages, sectionIndex = 0, charOffset = 14))
        assertEquals(2, pageIndexFor(pages, sectionIndex = 1, charOffset = 0))
        assertTrue(pageIndexFor(pages, sectionIndex = 0, charOffset = 0) == 0)
    }

    @Test
    fun `leaf destination follows the turn sign`() {
        assertEquals(4, leafDestination(pageIndex = 3, pageCount = 10, turn = -0.4f))
        assertEquals(2, leafDestination(pageIndex = 3, pageCount = 10, turn = 0.4f))
        assertEquals(3, leafDestination(pageIndex = 3, pageCount = 10, turn = 0f))
        assertEquals(9, leafDestination(pageIndex = 9, pageCount = 10, turn = -1f))
        assertEquals(0, leafDestination(pageIndex = 0, pageCount = 10, turn = 1f))
    }

    @Test
    fun `remaining page shrinks from the free edge`() {
        val next = remainingPageRect(100f, 50f, turn = -0.25f)
        assertEquals(0f, next.left)
        assertEquals(75f, next.right)
        val prev = remainingPageRect(100f, 50f, turn = 0.25f)
        assertEquals(25f, prev.left)
        assertEquals(100f, prev.right)
        assertEquals(75f, curlFoldX(100f, -0.25f))
        assertEquals(25f, curlFoldX(100f, 0.25f))
    }
}
