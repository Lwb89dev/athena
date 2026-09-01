package com.athena.reader.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedScopeTest {

    @Test
    fun `home feed is following, not global`() {
        assertEquals(FeedScope.Following, LibraryUiState().scope)
    }
}
