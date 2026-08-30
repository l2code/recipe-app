package com.recipearchive.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQuerySanitizerTest {

    @Test
    fun `single word becomes a prefix token`() {
        assertEquals("chicken*", SearchQuerySanitizer.sanitize("chicken"))
    }

    @Test
    fun `multiple words become multiple prefix tokens`() {
        assertEquals("chicken* soup*", SearchQuerySanitizer.sanitize("chicken soup"))
    }

    @Test
    fun `apostrophes split into separate tokens, matching how FTS indexes them`() {
        assertEquals("grandma* s*", SearchQuerySanitizer.sanitize("grandma's"))
    }

    @Test
    fun `quotes and colons are stripped`() {
        assertEquals("title*", SearchQuerySanitizer.sanitize("\"title:\""))
    }

    @Test
    fun `blank query sanitizes to null`() {
        assertNull(SearchQuerySanitizer.sanitize("   "))
    }

    @Test
    fun `empty query sanitizes to null`() {
        assertNull(SearchQuerySanitizer.sanitize(""))
    }

    @Test
    fun `punctuation-only query sanitizes to null`() {
        assertNull(SearchQuerySanitizer.sanitize("!!! ---"))
    }

    @Test
    fun `extra whitespace between words is collapsed`() {
        assertEquals("chicken* soup*", SearchQuerySanitizer.sanitize("  chicken   soup  "))
    }
}
