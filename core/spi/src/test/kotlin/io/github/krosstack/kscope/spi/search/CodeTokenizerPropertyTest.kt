package io.github.krosstack.kscope.spi.search

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeTokenizerPropertyTest {
    private val random = Random(SEED)

    private fun word(): String {
        val length = random.nextInt(MIN_LETTERS, MAX_LETTERS + 1)
        return String(CharArray(length) { LETTERS.random(random) })
    }

    private fun parts(): List<String> = List(random.nextInt(2, MAX_PARTS + 1)) { word() }

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }

    private fun renderings(parts: List<String>): List<String> = listOf(
        parts.first() + parts.drop(1).joinToString("") { it.capitalized() },
        parts.joinToString("") { it.capitalized() },
        parts.joinToString("_"),
        parts.joinToString("-"),
        parts.joinToString("_") { it.uppercase() },
    )

    private fun randomText(): String = String(CharArray(random.nextInt(0, MAX_TEXT)) { ALPHABET.random(random) })

    @Test
    fun `one name written in any style gives the same terms`() {
        repeat(CASES) {
            val written = renderings(parts())
            val terms = written.map(CodeTokenizer::tokens)
            assertTrue(terms.all { style -> style == terms.first() }, "$written gave $terms")
        }
    }

    @Test
    fun `the whole identifier is the parts joined, whatever the style`() {
        repeat(CASES) {
            val name = parts()
            renderings(name).forEach { style ->
                assertEquals(listOf(name.joinToString("")), CodeTokenizer.identifiers(style))
            }
        }
    }

    @Test
    fun `every term is lower case, at least two characters long and holds letters and digits only`() {
        repeat(CASES) {
            val text = randomText()
            CodeTokenizer.tokens(text).forEach { term ->
                assertTrue(term.codePointCount(0, term.length) >= 2, "'$term' of '$text'")
                assertTrue(term == term.lowercase(), "'$term' of '$text'")
                assertTrue(term.all { it.isLetterOrDigit() }, "'$term' of '$text'")
            }
        }
    }

    @Test
    fun `the terms of two texts joined by a space are the terms of each in turn`() {
        repeat(CASES) {
            val first = randomText()
            val second = randomText()
            assertEquals(
                CodeTokenizer.tokens(first) + CodeTokenizer.tokens(second),
                CodeTokenizer.tokens("$first $second"),
            )
        }
    }

    @Test
    fun `the index text is the terms of the text separated by spaces`() {
        repeat(CASES) {
            val text = randomText()
            assertEquals(CodeTokenizer.tokens(text).joinToString(" "), CodeTokenizer.indexText(text))
        }
    }

    private companion object {
        const val SEED = 20_260_605
        const val CASES = 400
        const val MIN_LETTERS = 2
        const val MAX_LETTERS = 7
        const val MAX_PARTS = 4
        const val MAX_TEXT = 40
        const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
        val ALPHABET = ("abcXYZ019 _-./,;:()'\"" + "éÜ日\t\n").toList()
    }
}
