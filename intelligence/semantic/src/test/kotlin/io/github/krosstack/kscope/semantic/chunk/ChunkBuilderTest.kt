package io.github.krosstack.kscope.semantic.chunk

import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.semantic.FakeEmbeddingProvider
import io.github.krosstack.kscope.semantic.SemanticFixture
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkBuilderTest {
    private val provider = FakeEmbeddingProvider()
    private val builder = ChunkBuilder(provider::cap)

    @Test
    fun `types files and sibling groups are canonical in any input order`() {
        val graph = SemanticFixture()
        val file = graph.file()
        val type = graph.symbol(file, "Sample", SymbolCategory.TYPE)
        val first = graph.symbol(file, "a", SymbolCategory.CALLABLE, 2)
        val second = graph.symbol(file, "b", SymbolCategory.VARIABLE, 3)
        graph.symbols.replaceAll { if (it == first || it == second) it.copy(container = type.id) else it }
        val chunks = builder.build(graph.reader, graph)
        assertEquals(setOf(ChunkKind.TYPE, ChunkKind.FILE, ChunkKind.GROUP), chunks.map { it.kind }.toSet())
        assertEquals(2, chunks.single { it.kind == ChunkKind.GROUP }.nodes.size)
        assertFalse("= 1" in chunks.single { it.kind == ChunkKind.TYPE }.text)
        graph.symbols.reverse()
        graph.artifacts.reverse()
        assertEquals(chunks, builder.build(graph.reader, graph))
    }

    @Test
    fun `declarations obey both caps without breaking UTF8 and outlines close with omitted count`() {
        val graph = SemanticFixture()
        val file = graph.file(text = "word ".repeat(1000))
        graph.symbol(file, "long", SymbolCategory.CALLABLE)
        repeat(600) { graph.symbol(file, "m$it", SymbolCategory.TYPE) }
        val chunks = builder.build(graph.reader, graph)
        assertTrue(chunks.all { provider.cap(it.text, 512) == it.text && it.text.toByteArray().size <= 32768 })
        assertTrue(chunks.single { it.kind == ChunkKind.FILE }.text.endsWith("more members"))
        val unicode = graph.file("unicode.kt", "😀".repeat(20000))
        graph.symbol(unicode, "unicode", SymbolCategory.CALLABLE)
        val declaration = builder.build(
            graph.reader,
            graph,
        ).single { it.artifact == unicode.ref && it.kind == ChunkKind.DECLARATION }
        assertTrue(declaration.text.toByteArray().size <= 32768)
        assertFalse(declaration.text.last().isHighSurrogate())
    }

    @Test
    fun `generated binary invalid UTF8 oversize and stale files are excluded`() {
        val graph = SemanticFixture()
        val generated = graph.file("generated.kt")
        graph.artifacts[0] = generated.copy(generated = GeneratedInfo("generator", null))
        graph.file("binary", "\u0000")
        val invalid = graph.file("invalid")
        graph.contents[invalid.ref] = byteArrayOf(0xC3.toByte())
        val hash = MessageDigest.getInstance("SHA-256").digest(graph.contents.getValue(invalid.ref))
        graph.artifacts[graph.artifacts.lastIndex] = invalid.copy(
            contentHash = ContentHash(HexFormat.of().formatHex(hash)),
        )
        val huge = graph.file("huge")
        graph.artifacts[graph.artifacts.lastIndex] = huge.copy(sizeBytes = 3_000_000)
        val stale = graph.file("stale")
        graph.contents[stale.ref] = "changed".toByteArray()
        assertEquals(emptyList(), builder.build(graph.reader, graph))
    }

    @Test
    fun `groups contain at most eight small siblings and preserve every node`() {
        val graph = SemanticFixture()
        val file = graph.file()
        repeat(19) { graph.symbol(file, "f$it", SymbolCategory.CALLABLE) }
        val declarations = builder.build(graph.reader, graph).filter { it.kind != ChunkKind.FILE }
        assertEquals(listOf(3, 8, 8), declarations.map { it.nodes.size }.sorted())
        assertEquals(19, declarations.flatMap { it.nodes }.distinct().size)
    }

    @Test
    fun `byte limited siblings stay complete and oversized headers retain outline closing line`() {
        val graph = SemanticFixture()
        val file = graph.file(text = "x".repeat(20000))
        repeat(2) { graph.symbol(file, "small$it", SymbolCategory.CALLABLE) }
        val chunks = builder.build(graph.reader, graph)
        assertEquals(2, chunks.count { it.kind == ChunkKind.DECLARATION })
        assertTrue(chunks.filter { it.kind == ChunkKind.DECLARATION }.all { it.text.endsWith("x".repeat(20000)) })
        val type = graph.symbol(file, "Huge", SymbolCategory.TYPE)
        graph.symbols[graph.symbols.lastIndex] = type.copy(documentation = "word ".repeat(600))
        graph.symbols[0] = graph.symbols[0].copy(container = type.id)
        val outline = builder.build(graph.reader, graph).single { it.kind == ChunkKind.TYPE }.text
        assertTrue(outline.endsWith("1 more members"))
        assertEquals(outline, provider.cap(outline, 512))
    }

    @Test
    fun `UTF8 byte boundary never splits a supplementary character`() {
        val capped = ChunkText(provider::cap).capped("x".repeat(32767) + "😀")
        assertEquals("x".repeat(32767), capped)
    }

    @Test
    fun `file outline preserves the full package declaration`() {
        val graph = SemanticFixture()
        val file = graph.file(text = "package actual.example")
        val packageSymbol = graph.symbol(file, "example", SymbolCategory.PACKAGE)
        graph.symbols[0] = packageSymbol.copy(qualifiedName = "actual.example")
        val chunk = builder.build(graph.reader, graph).single()
        assertTrue("package actual.example" in chunk.text)
    }
}
