package com.athena.reader.data.session

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPrefsTest {

    @Test
    fun `zoom scales the type size`() {
        val prefs = ReaderPrefs(fontSize = 20, zoomPercent = 150)
        assertEquals(30, prefs.effectiveFontSize)
    }

    @Test
    fun `clamps type size, zoom and margins`() {
        val prefs = ReaderPrefs()
            .withFontSize(4)
            .withZoom(400)
            .withMargin(3)
            .withLineHeight(0.5f)
        assertEquals(ReaderPrefs.MIN_FONT_SIZE, prefs.fontSize)
        assertEquals(ReaderPrefs.MAX_ZOOM, prefs.zoomPercent)
        assertEquals(ReaderPrefs.MIN_MARGIN, prefs.marginDp)
        assertEquals(ReaderPrefs.MIN_LINE_HEIGHT, prefs.lineHeight)
    }

    @Test
    fun `default turn mode is paged`() {
        assertEquals(ReaderTurnMode.Paged, ReaderPrefs().turnMode)
    }
}
