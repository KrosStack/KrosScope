package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.Section
import kotlin.test.Test
import kotlin.test.assertTrue

class AnalyzeRenderingTest {
    @Test
    fun `a standard result lists the relevant files and counts what it left out`() {
        Golden.assertMatches("render-analyze-standard", ResultFixtures.render(ResultFixtures.captured()))
    }

    @Test
    fun `a brief result fits one screen with the best five files the test count and the next step`() {
        val text = ResultFixtures.render(ResultFixtures.captured(DetailLevel.BRIEF))

        Golden.assertMatches("render-analyze-brief", text)
        assertTrue(text.lines().size <= SCREEN_LINES, text)
    }

    @Test
    fun `a full result lists every relevant file instead of ten`() {
        val text = ResultFixtures.render(ResultFixtures.captured(DetailLevel.FULL))

        Golden.assertMatches("render-analyze-full", text)
        assertTrue(!text.contains("more relevant"), text)
    }

    @Test
    fun `named sections print alone`() {
        val context = ResultFixtures.captured(sections = setOf(Section.TESTS, Section.DOCUMENTS))

        Golden.assertMatches("render-analyze-sections", ResultFixtures.render(context))
    }

    @Test
    fun `on a terminal the header is bold and coloured and long paths are shortened in the middle`() {
        val text = ResultFixtures.render(ResultFixtures.terminal(width = TERMINAL_WIDTH))

        Golden.assertMatches("render-analyze-terminal", ResultFixtures.visible(text))
    }

    @Test
    fun `a result without files says that nothing matches`() {
        val empty = ResultFixtures.analysis(files = emptyList())

        Golden.assertMatches("render-analyze-nothing", AnalyzeText.render(empty, ResultFixtures.captured()))
    }

    private companion object {
        const val SCREEN_LINES = 24
        const val TERMINAL_WIDTH = 100
    }
}
