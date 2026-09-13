package us.panks.opencode.processor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeneratedSourceEscapingTest {
    @Test
    fun escapesStringTemplatesAndControlCharacters() {
        assertEquals(
            "\"\\\${danger}\\u0000\\u0008\\u000C\"",
            kotlinStringLiteral("\${danger}\u0000\b\u000C"),
        )
    }

    @Test
    fun escapesKotlinKeywordsAsIdentifiers() {
        assertEquals("normalName", kotlinIdentifier("normalName"))
        assertEquals("`when`", kotlinIdentifier("when"))
        assertEquals("`handler name`", kotlinIdentifier("handler name"))
        assertFailsWith<IllegalArgumentException> { kotlinIdentifier("bad\u007fidentifier") }
        assertFailsWith<IllegalArgumentException> { kotlinIdentifier("bad\u2028identifier") }
    }
}
