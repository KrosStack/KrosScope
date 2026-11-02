package io.github.krosstack.kscope.context.token

import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.context.selection.CountingTokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ExactMarkdownTokensTest {
    @Test
    fun `identical complete Markdown uses its exact cached BPE count`() {
        val estimator = CountingTokenEstimator(TokenEstimators.create())
        val cache = ExactMarkdownTokens(estimator)
        val text = "Task α🙂漢字\n\n```` source/Example\n```\n````\n"
        val first = cache.count(text)

        assertEquals(first, cache.count(text))
        assertEquals(1, estimator.calls)
        assertEquals(estimator.count(text), first)
    }

    @Test
    fun `LRU entry limit evicts old text without changing exact counts`() {
        val estimator = CountingTokenEstimator(TokenEstimators.create())
        val cache = ExactMarkdownTokens(estimator, maxEntries = 2)
        val first = cache.count("first")
        cache.count("second")
        cache.count("first")
        cache.count("third")

        assertEquals(first, cache.count("first"))
        assertEquals(3, estimator.calls)
        cache.count("second")
        assertEquals(4, estimator.calls)
    }

    @Test
    fun `character cap does not retain oversized rendered packages`() {
        val estimator = CountingTokenEstimator(TokenEstimators.create())
        val cache = ExactMarkdownTokens(estimator, maxCharacters = 4)
        val expected = cache.count("α🙂漢字 Unicode")

        assertEquals(expected, cache.count("α🙂漢字 Unicode"))
        assertEquals(2, estimator.calls)
    }
}
