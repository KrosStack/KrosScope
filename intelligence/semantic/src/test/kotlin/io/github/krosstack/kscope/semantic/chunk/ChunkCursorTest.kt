package io.github.krosstack.kscope.semantic.chunk

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.semantic.FakeEmbeddingProvider
import io.github.krosstack.kscope.semantic.SemanticFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChunkCursorTest {
    @Test
    fun `small declarations are capped once and only as the next batch needs`() {
        val graph = SemanticFixture()
        val source = (0 until 100).joinToString("\n") { "fun f$it() = $it" }
        val file = graph.file(text = source)
        val inputs = (0 until 100).map {
            graph.symbol(file, "f$it", SymbolCategory.CALLABLE, it + 1)
            "sample.kt\nfun f$it()\nDocumentation for f$it\nfun f$it() = $it"
        }.toSet()
        val calls = mutableListOf<Pair<String, Int>>()
        val provider = FakeEmbeddingProvider()
        val cursor = ChunkBuilder { text, tokens ->
            calls += text to tokens
            provider.cap(text, tokens)
        }.cursor(file, graph.symbols, source)
        val first = cursor.next(1, 32768)
        assertEquals(8, first.single().nodes.size)
        assertEquals(8, calls.count { (text, tokens) -> text in inputs && tokens == 512 })
        assertTrue(calls.size < 32)
        val chunks = first.toMutableList()
        while (!cursor.complete) chunks += cursor.next(1, 32768)
        val declarations = calls.filter { (text, tokens) -> text in inputs && tokens == 512 }
        assertEquals(inputs.associateWith { 1 }, declarations.groupingBy { it.first }.eachCount())
        val nodes = chunks.flatMap { it.nodes }.filterIsInstance<NodeRef.OfSymbol>()
        assertEquals(100, nodes.distinct().size)
    }

    @Test
    fun `cursor matches the existing builder for files types and groups`() {
        val graph = SemanticFixture()
        val file = graph.file()
        val type = graph.symbol(file, "Sample", SymbolCategory.TYPE)
        repeat(19) { graph.symbol(file, "f$it", SymbolCategory.CALLABLE, 2) }
        graph.symbols.replaceAll { if (it != type) it.copy(container = type.id) else it }
        val builder = ChunkBuilder(FakeEmbeddingProvider()::cap)
        val expected = builder.build(graph.reader, graph)
        val cursor = builder.cursor(file, graph.symbols.reversed(), graph.read(file.ref).toString(Charsets.UTF_8))
        val actual = mutableListOf<Chunk>()
        while (!cursor.complete) actual += cursor.next(1, 32768)
        assertEquals(expected, actual.sortedWith(compareBy({ it.key }, { KscopeJson.encodeToString(it.nodes) })))
        assertTrue(cursor.next(1, 32768).isEmpty())
    }

    @Test
    fun `cursor respects text and byte bounds without retaining later texts`() {
        val graph = SemanticFixture()
        val source = "\uD83D\uDE00 ".repeat(20000)
        val file = graph.file(text = source)
        repeat(40) { graph.symbol(file, "f$it", SymbolCategory.CALLABLE) }
        var calls = 0
        val provider = FakeEmbeddingProvider()
        val builder = ChunkBuilder { text, tokens ->
            calls++
            provider.cap(text, tokens)
        }
        val cursor = builder.cursor(file, graph.symbols, source)
        assertEquals(0, calls)
        assertFailsWith<IllegalArgumentException> { cursor.next(0, 32768) }
        assertFailsWith<IllegalArgumentException> { cursor.next(1, 32767) }
        val first = cursor.next(1, 32768)
        assertEquals(1, first.size)
        assertEquals(2, calls)
        val batches = mutableListOf(first)
        while (!cursor.complete) batches += cursor.next(16, 65536)
        assertTrue(batches.all { batch -> batch.size <= 16 && batch.sumOf { it.text.toByteArray().size } <= 65536 })
        assertTrue(batches.flatten().all { it.text.lastOrNull()?.isHighSurrogate() != true })
        assertEquals(41, batches.flatten().size)
    }
}
