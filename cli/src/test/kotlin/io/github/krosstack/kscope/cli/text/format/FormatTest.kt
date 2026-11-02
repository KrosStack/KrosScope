package io.github.krosstack.kscope.cli.text.format

import io.github.krosstack.kscope.cli.resolve
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test
    fun `counts have thousands separators whatever the default locale is`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1,234,567", Format.count(1_234_567))
            assertEquals("999", Format.count(999L))
            assertEquals("0.500", Format.decimal(0.5, 3))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `durations are given in the unit a person reads`() {
        assertEquals("0 ms", Durations.human(0))
        assertEquals("850 ms", Durations.human(850))
        assertEquals("1.0 s", Durations.human(1_000))
        assertEquals("9.9 s", Durations.human(9_949))
        assertEquals("10 s", Durations.human(10_000))
        assertEquals("59 s", Durations.human(58_600))
        assertEquals("1 m 0 s", Durations.human(60_000))
        assertEquals("2 m 14 s", Durations.human(134_000))
        assertEquals("1 h 5 m", Durations.human(3_900_000))
    }

    @Test
    fun `nouns agree with their number`() {
        assertEquals("1 file", Format.noun(1, "file"))
        assertEquals("2 files", Format.noun(2, "file"))
        assertEquals("1,000 files", Format.noun(1_000, "file"))
        assertEquals("1 match", Format.noun(1, "match", "matches"))
        assertEquals("0 matches", Format.noun(0, "match", "matches"))
    }

    @Test
    fun `text is clipped to one line and long lists are cut`() {
        assertEquals("a b c", Format.clip("  a\n b\t c ", 20))
        assertEquals("abcdefg...", Format.clip("abcdefghijklmnop", 10))
        assertEquals("a, b, c", Format.listing(listOf("a", "b", "c"), 3))
        assertEquals("a, b, +2 more", Format.listing(listOf("a", "b", "c", "d"), 2))
        assertEquals("55%", Format.percent(5, 9))
        assertEquals("0%", Format.percent(0, 0))
    }

    @Test
    fun `paths are shown relative to the workspace when they are inside it`() {
        val workspace = Path.of("work", "space").toAbsolutePath()
        val context = TextContext(workspace, Emphasis.NONE)

        assertEquals(".kscope", context.relative(workspace.resolve(".kscope").toString().replace('\\', '/')))
        assertEquals("a/b", context.relative(workspace.resolve("a/b").toString()))
        val outside = Path.of("elsewhere").toAbsolutePath().toString()
        assertEquals(outside, context.relative(outside))
        assertEquals("not a path at all?", context.relative("not a path at all?"))
    }

    @Test
    fun `a table aligns its columns and trims the ends of its lines`() {
        val table = Table(listOf(Column("name"), Column("n", Align.RIGHT), Column("note")))
            .row("alpha", "7", "")
            .row("b", "1,234", "x")

        assertEquals(
            listOf("  name       n  note", "  alpha      7", "  b      1,234  x"),
            table.render(),
        )
    }

    @Test
    fun `emphasis wraps text only when it is enabled`() {
        val escape = 27.toChar()

        assertEquals("text", Emphasis.NONE.bold("text"))
        assertEquals("$escape[1mtext$escape[0m", Emphasis(true).bold("text"))
        assertEquals("", Emphasis(true).bold(""))
        assertEquals("text", Emphasis(false).bad("text"))
    }
}
