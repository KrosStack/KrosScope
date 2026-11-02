package io.github.krosstack.kscope.cli.text

import io.github.krosstack.kscope.cli.text.format.Readings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyzeRenderingRulesTest {
    private val escape = 27.toChar()

    @Test
    fun `NO_COLOR keeps the width and drops the colour`() {
        val text = ResultFixtures.render(ResultFixtures.terminal(width = WIDTH, emphasised = false))

        assertFalse(text.contains(escape), text)
        assertTrue(text.lines().all { it.length <= WIDTH }, text)
        assertTrue(text.contains("/.../"), text)
    }

    @Test
    fun `the header and nothing else is coloured on a terminal that shows colour`() {
        val text = ResultFixtures.render(ResultFixtures.terminal(width = WIDTH))
        val coloured = text.lines().filter { it.contains("$escape[1m$escape[36m") }

        assertEquals(3, coloured.size, text)
        assertTrue(coloured.all { it.contains("score") }, text)
    }

    @Test
    fun `captured output has full paths and no escape code`() {
        val text = ResultFixtures.render(ResultFixtures.captured())
        val longest = "data/settings/src/test/kotlin/sample/tasktide/data/settings/RoomPreferenceRepositoryTest.kt"

        assertFalse(text.contains(escape), text)
        assertFalse(text.contains("..."), text)
        assertTrue(text.contains(longest), text)
    }

    @Test
    fun `images are counted in one line and never listed as files to edit or read`() {
        val text = ResultFixtures.render(ResultFixtures.captured())

        assertFalse(text.contains(".png"), text)
        assertEquals(1, text.lines().count { it.contains("Not listed: 2 images and other binary files.") }, text)
    }

    @Test
    fun `documents have their own section`() {
        val text = ResultFixtures.render(ResultFixtures.captured())

        assertTrue(text.substringAfter("Documents (1)").substringBefore("\n\n").contains("README.md"), text)
        assertFalse(text.substringBefore("Documents (1)").contains("README.md"), text)
    }

    @Test
    fun `the headline counts the relevant files and the files scored`() {
        val headline = ResultFixtures.render(ResultFixtures.captured()).lines()[1]

        assertEquals(
            "2 files to edit and 11 to read, touching 2 modules; 36 files scored; several close candidates.",
            headline,
        )
    }

    @Test
    fun `the first line says which reading was used`() {
        val missed = Readings.taskAfterMiss("Lab")

        assertEquals(Readings.TASK, ResultFixtures.render(ResultFixtures.captured()).lines().first())
        assertEquals(missed, ResultFixtures.render(ResultFixtures.captured(), missed).lines().first())
    }

    private companion object {
        const val WIDTH = 100
    }
}
