package io.github.krosstack.kscope.cli.text.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableFitTest {
    private val path = "feature/tasks/src/main/kotlin/sample/tasktide/feature/tasks/TaskListViewModel.kt"

    @Test
    fun `a path is shortened in the middle and keeps its start and its file name`() {
        val located = "data/db/src/commonMain/kotlin/sample/tasktide/data/db/TaskRepository.kt:14"

        assertEquals(located, PathShortening.shorten(located, located.length))
        assertEquals("data/db/src/commonMain/.../TaskRepository.kt:14", PathShortening.shorten(located, 48))
        assertEquals("data/db/.../TaskRepository.kt:14", PathShortening.shorten(located, 33))
        assertEquals(".../TaskRepository.kt:14", PathShortening.shorten(located, 25))
        assertEquals("...ository.kt:14", PathShortening.shorten(located, 16))
        assertEquals("noDirectory.kt", PathShortening.shorten("noDirectory.kt", 4))
    }

    @Test
    fun `a table gives its path column what is left of the width and never cuts the others`() {
        val table = Table(listOf(Column("score", Align.RIGHT), Column("file", fit = Fit.PATH), Column("because")))
            .row("0.087", path, "lexical symbol, graph")

        val plain = table.render()
        val fitted = table.render(width = WIDTH)

        assertEquals("  score  file${" ".repeat(path.length - "file".length)}  because", plain.first())
        assertTrue(plain.last().contains(path))
        assertTrue(fitted.all { it.length <= WIDTH }, fitted.joinToString("\n"))
        assertTrue(fitted.last().endsWith("lexical symbol, graph"))
        assertTrue(fitted.last().contains("TaskListViewModel.kt"))
    }

    @Test
    fun `a table never shortens a path below the minimum width`() {
        val lines = Table(listOf(Column("file", fit = Fit.PATH))).row(path).render(width = 10)

        assertTrue(lines.last().trim().length >= Table.MINIMUM_PATH_WIDTH - 3, lines.last())
    }

    @Test
    fun `the header is emphasised on a terminal and plain otherwise`() {
        val escape = 27.toChar()
        val table = Table(listOf(Column("file"), Column("n", Align.RIGHT))).row("a", "1")

        assertEquals("  file  n", table.render().first())
        assertEquals("$escape[1m$escape[36m  file  n$escape[0m", table.render(emphasis = Emphasis(true)).first())
        assertEquals("  a     1", table.render(emphasis = Emphasis(true)).last())
    }

    private companion object {
        const val WIDTH = 70
    }
}
