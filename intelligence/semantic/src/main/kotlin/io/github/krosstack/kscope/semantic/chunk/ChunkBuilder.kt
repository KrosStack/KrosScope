package io.github.krosstack.kscope.semantic.chunk

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat

public class ChunkBuilder(cap: (String, Int) -> String) {
    private val text = ChunkText(cap)

    public fun build(reader: GraphReader, files: WorkspaceFiles): List<Chunk> = reader.artifacts()
        .sortedBy { KscopeJson.encodeToString(it.ref) }.flatMap { artifact ->
            val source = source(artifact, files)
            if (source == null) emptyList() else chunks(artifact, source, reader)
        }.sortedWith(compareBy({ it.key }, { KscopeJson.encodeToString(it.nodes) }))

    private fun chunks(artifact: Artifact, source: String, reader: GraphReader): List<Chunk> {
        val symbols = reader.symbols(SymbolQuery(artifact = artifact.ref, limit = Int.MAX_VALUE))
            .filter { it.declaration?.artifact == artifact.ref && it.origin == SymbolOrigin.Source }
            .sortedBy { KscopeJson.encodeToString(it) }
        val lines = source.split('\n')
        val chunks = symbols.mapNotNull { symbol -> symbolChunk(symbol, symbols, lines, artifact) }
        val packages = symbols.filter { it.container == null }.mapNotNull {
            if (it.kind.category == SymbolCategory.PACKAGE) {
                it.qualifiedName ?: it.name
            } else {
                it.qualifiedName?.substringBeforeLast('.', "")
            }
        }.filter { it.isNotEmpty() }.distinct().sorted()
            .joinToString("\n") { "package $it" }
        val file = text.chunk(
            ChunkKind.FILE,
            text.outline("${artifact.ref.path.value}\n$packages", symbols),
            listOf(NodeRef.OfArtifact(artifact.ref)),
            artifact.ref,
        )
        return ChunkGroups(text).merge(chunks, symbols) + file
    }

    private fun symbolChunk(symbol: Symbol, symbols: List<Symbol>, lines: List<String>, artifact: Artifact): Chunk? {
        val kind = when (symbol.kind.category) {
            SymbolCategory.TYPE -> ChunkKind.TYPE
            SymbolCategory.CALLABLE, SymbolCategory.VARIABLE -> ChunkKind.DECLARATION
            else -> return null
        }
        val content = if (kind == ChunkKind.TYPE) {
            text.outline(text.header(symbol), symbols.filter { it.container == symbol.id })
        } else {
            text.declaration(symbol, lines)
        }
        return text.chunk(kind, content, listOf(NodeRef.OfSymbol(symbol.id)), artifact.ref)
    }

    private fun source(artifact: Artifact, files: WorkspaceFiles): String? {
        if (artifact.generated != null || artifact.sizeBytes > MAX_SOURCE_BYTES) return null
        return try {
            val bytes = files.read(artifact.ref)
            val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            if (bytes.size > MAX_SOURCE_BYTES || digest != artifact.contentHash.value) {
                null
            } else {
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().takeUnless { '\u0000' in it }
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 2 * 1024 * 1024
    }
}
