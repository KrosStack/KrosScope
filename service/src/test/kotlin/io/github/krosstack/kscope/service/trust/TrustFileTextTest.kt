package io.github.krosstack.kscope.service.trust

import org.tomlj.Toml
import org.tomlj.TomlArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TrustFileTextTest {
    private fun entriesOf(text: String): List<Any?> {
        val toml = Toml.parse(text)
        assertFalse(toml.hasErrors(), toml.errors().joinToString())
        val array = toml.get(listOf(TrustFileText.KEY)) as TomlArray
        return (0 until array.size()).map(array::get)
    }

    @Test
    fun `every character that TOML forbids in a string is escaped and read back`() {
        val forbidden = listOf(0x00, 0x07, 0x09, 0x0a, 0x1f, 0x7f).joinToString("") { it.toChar().toString() }
        val workspaces = listOf("/work/a${forbidden}b", "/work/quote\"and\\backslash")

        val text = TrustFileText.render(workspaces)

        assertEquals(workspaces.sorted(), entriesOf(text))
    }

    @Test
    fun `the delete character is written as an escape`() {
        val text = TrustFileText.render(listOf("/work/a\u007fb"))

        assertFalse('\u007f' in text)
        assertEquals(listOf<Any?>("/work/a\u007fb"), entriesOf(text))
    }
}
