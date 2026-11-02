package io.github.krosstack.kscope.context

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TokenCountStoreTest {
    private val text = "package sample\n\nclass Greeter {\n    fun greet() = \"hello\"\n}\n"
    private val file = sourceArtifact("source/Greeter", text)
    private val symbol = declaration("Greeter", file, TextRange(3, 1, 5, 2), "class Greeter")
    private val fixture = ContextFixture(listOf(file), listOf(symbol), mapOf(file.ref to text))
    private val input = ContextInput(
        "greet",
        listOf(ContextCandidate(NodeRef.OfArtifact(file.ref), 1.0), ContextCandidate(NodeRef.OfSymbol(symbol.id), 0.5)),
    )

    private class RecordingStore : TokenCountStore {
        val entries = mutableMapOf<TokenCountKey, TokenCount>()
        var recalled = 0

        override fun recall(key: TokenCountKey): TokenCount? = entries[key]?.also { recalled++ }

        override fun remember(key: TokenCountKey, count: TokenCount) {
            entries[key] = count
        }
    }

    private class CountingEstimator : TokenEstimator {
        var counted = 0
        override val id = TokenizerId("test_counting")

        override fun count(text: String): TokenCount {
            counted++
            return TokenCount(text.length / 3)
        }

        override fun truncate(text: String, maxTokens: TokenCount): String = text.take(maxTokens.value)
    }

    private fun compile(estimator: TokenEstimator, store: TokenCountStore): ContextPackage =
        ContextCompiler(fixture, fixture, estimator, store).compile(input, ContextLevel.FULL)

    @Test
    fun `a warm store gives the package of a cold store and of no store`() {
        val store = RecordingStore()

        val none = compile(CountingEstimator(), TokenCountStore.NONE)
        val cold = compile(CountingEstimator(), store)
        val warm = compile(CountingEstimator(), store)

        assertEquals(none, cold)
        assertEquals(none, warm)
        assertTrue(store.entries.isNotEmpty())
        assertTrue(store.recalled > 0)
    }

    @Test
    fun `a warm store spares the estimator the counts it already holds`() {
        val store = RecordingStore()
        val cold = CountingEstimator()
        val warm = CountingEstimator()

        compile(cold, store)
        compile(warm, store)

        assertTrue(warm.counted < cold.counted, "cold ${cold.counted}, warm ${warm.counted}")
    }

    @Test
    fun `the key names the content hash the rendering the estimator and a digest of the counted text`() {
        val store = RecordingStore()

        compile(CountingEstimator(), store)

        val fileKeys = store.entries.keys.filter { it.contentHash == file.contentHash }
        assertTrue(fileKeys.isNotEmpty())
        assertTrue(fileKeys.all { it.estimator == TokenizerId("test_counting") })
        assertEquals(fileKeys.size, fileKeys.map { it.textDigest to it.rendering }.toSet().size)
        assertTrue(fileKeys.all { it.textDigest.length == 64 })
    }

    @Test
    fun `an entry for other text never answers for this text`() {
        val store = RecordingStore()
        compile(CountingEstimator(), store)
        val poisoned = RecordingStore()
        store.entries.forEach { (key, count) -> poisoned.entries[key.copy(textDigest = "0".repeat(64))] = count }

        val estimator = CountingEstimator()
        val result = compile(estimator, poisoned)

        assertEquals(compile(CountingEstimator(), TokenCountStore.NONE), result)
        assertEquals(0, poisoned.recalled)
    }

    @Test
    fun `an entry counted by another estimator is not recalled`() {
        val store = RecordingStore()
        compile(CountingEstimator(), store)

        val other = object : TokenEstimator {
            override val id = TokenizerId("test_other")

            override fun count(text: String): TokenCount = TokenCount(text.length)

            override fun truncate(text: String, maxTokens: TokenCount): String = text.take(maxTokens.value)
        }
        val result = compile(other, store)

        assertEquals(compile(other, TokenCountStore.NONE), result)
    }
}
