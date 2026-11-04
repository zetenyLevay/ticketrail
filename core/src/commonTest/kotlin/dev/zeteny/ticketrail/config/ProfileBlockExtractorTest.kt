package dev.zeteny.ticketrail.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileBlockExtractorTest {

    private fun extractFailure(markdown: String): ProfileException {
        val result = ProfileBlockExtractor.extract(markdown)
        assertTrue(result.isFailure, "expected a failure for:\n$markdown")
        return assertIs<ProfileException>(result.exceptionOrNull())
    }

    @Test
    fun extractsTheBlockAndIgnoresTheRest() {
        val markdown = """
            ---
            type: reference
            ---
            # Ticket profile

            ```ticket-profile
            spec_version: 5
            queues: [home, work]
            ```

            Some notes.
        """.trimIndent()

        assertEquals("spec_version: 5\nqueues: [home, work]", ProfileBlockExtractor.extract(markdown).getOrThrow())
    }

    @Test
    fun handlesTildeFencesAndCrlf() {
        val markdown = "~~~~ticket-profile\r\nspec_version: 5\r\n~~~~\r\n"
        assertEquals("spec_version: 5", ProfileBlockExtractor.extract(markdown).getOrThrow())
    }

    @Test
    fun ignoresAProfileFenceInsideAnotherBlock() {
        val markdown = """
            ````markdown
            ```ticket-profile
            spec_version: 1
            ```
            ````

            ```ticket-profile
            spec_version: 5
            ```
        """.trimIndent()

        assertEquals("spec_version: 5", ProfileBlockExtractor.extract(markdown).getOrThrow())
    }

    @Test
    fun removesTheFenceIndent() {
        val markdown = "  ```ticket-profile\n  queues:\n    - home\n  ```"
        assertEquals("queues:\n  - home", ProfileBlockExtractor.extract(markdown).getOrThrow())
    }

    @Test
    fun noBlockIsRefused() {
        val e = extractFailure("# Profile\n\n```yaml\nspec_version: 5\n```")
        assertTrue("no ticket-profile block" in e.message!!)
    }

    @Test
    fun twoBlocksAreRefused() {
        val e = extractFailure("```ticket-profile\na: 1\n```\n\n```ticket-profile\nb: 2\n```")
        assertTrue("2 ticket-profile blocks" in e.message!!)
    }

    @Test
    fun unclosedBlockIsRefused() {
        val e = extractFailure("```ticket-profile\nspec_version: 5\n")
        assertTrue("never closed" in e.message!!)
    }
}
