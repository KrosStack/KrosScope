package io.github.krosstack.kscope.benchmark.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class PlainTextSearchTest {
    @Test
    fun `baseline stems raw text without adding identifier parts or paths`() {
        PlainTextSearch(mapOf("needle" to "unrelated", "a" to "needleFactory", "b" to "needles")).use { index ->
            assertEquals(listOf("b"), index.search("needle"))
            assertEquals(listOf("a"), index.search("needleFactory"))
            assertEquals(emptyList(), index.search("factory"))
        }
    }

    @Test
    fun `equal scores use path ordering and query syntax is treated as text`() {
        PlainTextSearch(mapOf("b" to "needle", "a" to "needle")).use { index ->
            assertEquals(listOf("a", "b"), index.search("\"needle\" OR NOT"))
            assertEquals(emptyList(), index.search("\" *"))
        }
    }

    @Test
    fun `English baseline removes standard stopwords from documents and queries before stemming`() {
        val documents = mapOf("a" to "the needles", "b" to "their unrelated", "c" to "translation label colour")
        PlainTextSearch(documents, removeEnglishStopWords = true).use { index ->
            assertEquals(listOf("a"), index.search("the needle"))
            assertEquals(emptyList(), index.search("their the"))
            assertEquals(listOf("c"), index.search("translation label colour"))
        }
    }
}
