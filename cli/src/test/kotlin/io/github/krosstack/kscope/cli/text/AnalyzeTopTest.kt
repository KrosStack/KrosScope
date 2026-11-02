package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Section
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyzeTopTest {
    private fun render(detail: DetailLevel): String =
        ResultFixtures.render(ResultFixtures.captured(detail, setOf(Section.TOP)))

    @Test
    fun `full top section lists every relevant code candidate without a row limit hint`() {
        val text = render(DetailLevel.FULL)
        val relevant = Listing.of(ResultFixtures.analysis(), ResultFixtures.kinds).code

        assertEquals(13, relevant.size)
        assertEquals(relevant.size, text.lines().count { it.contains(".kt") }, text)
        relevant.forEach { assertTrue(text.contains(it.artifact.path.value), text) }
        assertFalse(text.contains("Generated"), text)
        assertFalse(text.contains("--detail full"), text)
    }

    @Test
    fun `standard and brief top sections keep the best five and the same remainder hint`() {
        val standard = render(DetailLevel.STANDARD)
        val brief = render(DetailLevel.BRIEF)
        val relevant = Listing.of(ResultFixtures.analysis(), ResultFixtures.kinds).code

        assertEquals(standard, brief)
        assertEquals(5, standard.lines().count { it.contains(".kt") }, standard)
        relevant.take(5).forEach { assertTrue(standard.contains(it.artifact.path.value), standard) }
        relevant.drop(5).forEach { assertFalse(standard.contains(it.artifact.path.value), standard) }
        assertTrue(standard.contains("8 more relevant (use --detail full to list them)"), standard)
    }
}
