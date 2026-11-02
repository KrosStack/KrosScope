package io.github.krosstack.kscope.cli.service.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LaunchArgumentsTest {
    private val awkward = listOf(
        "plain",
        "with space",
        "back\\slash\\",
        "C:\\Program Files\\Java\\bin\\javaw.exe",
        "say \"hi\"",
        "it's",
        "#not a comment",
        "",
        "tab\there",
        "two\nlines",
        "-Dkscope.service.home=C:\\Users\\me\\.kscope\\service dir",
    )

    @Test
    fun `every argument is written on a line of its own and read back as it was`() {
        val text = LaunchArguments.render(awkward)

        assertEquals(awkward, LaunchArguments.parse(text))
        assertEquals(awkward.size + 1, text.trimEnd().lines().size)
    }

    @Test
    fun `a value with a space or a backslash is quoted with its backslashes doubled`() {
        val lines = LaunchArguments.render(listOf("a b", "c\\d", "plain")).lines()

        assertEquals(listOf("\"a b\"", "\"c\\\\d\"", "plain"), lines.drop(1).take(3))
    }

    @Test
    fun `the file starts with a comment that says who wrote it`() {
        val first = LaunchArguments.render(listOf("x")).lines().first()

        assertTrue(first.startsWith("# "), first)
        assertEquals(listOf("x"), LaunchArguments.parse(LaunchArguments.render(listOf("x"))))
    }

    @Test
    fun `no arguments give a file that reads back as none`() {
        assertEquals(emptyList(), LaunchArguments.parse(LaunchArguments.render(emptyList())))
    }
}
