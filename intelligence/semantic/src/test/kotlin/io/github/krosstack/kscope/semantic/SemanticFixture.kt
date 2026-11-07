package io.github.krosstack.kscope.semantic

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.semantic.chunk.ChunkBuilder
import io.github.krosstack.kscope.semantic.search.ScoredChunk
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationHeader
import io.github.krosstack.kscope.semantic.store.Quantized
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

internal class SemanticFixture : WorkspaceFiles {
    val contents = mutableMapOf<ArtifactRef, ByteArray>()
    val artifacts = mutableListOf<Artifact>()
    val symbols = mutableListOf<Symbol>()

    fun file(path: String = "sample.kt", text: String = "class Sample {\nfun a() = 1\nfun b() = 2\n}"): Artifact {
        val bytes = text.toByteArray()
        val ref = ArtifactRef(RepositoryId("repo"), RepoPath(path))
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        val artifact = Artifact(
            ref,
            ArtifactType("kotlin.source", ArtifactCategory.SOURCE),
            ContentHash(digest),
            bytes.size.toLong(),
            text.lines().size,
            null,
            null,
        )
        contents[ref] = bytes
        artifacts.removeAll { it.ref == ref }
        artifacts += artifact
        return artifact
    }

    fun symbol(file: Artifact, name: String, category: SymbolCategory, line: Int = 1): Symbol {
        val location = SourceLocation(
            file.ref,
            TextRange(
                line,
                1,
                line,
                contents.getValue(file.ref)
                    .toString(Charsets.UTF_8).lines()[line - 1].length + 1,
            ),
        )
        return Symbol(
            SymbolId(name), SymbolKind("kotlin.${category.name.lowercase()}", category), name, "sample.$name",
            if (category == SymbolCategory.TYPE) "class $name" else "fun $name()", null, null, emptySet(),
            SymbolOrigin.Source, location, location, "Documentation for $name", Scope(),
            EvidenceClass.COMPILER, Certainty.EXACT,
        ).also { symbols += it }
    }

    fun generation(): GenerationFile {
        val provider = FakeEmbeddingProvider()
        val chunks = ChunkBuilder(provider::cap).build(reader, this)
        return GenerationFile(
            GenerationHeader(provider.id, provider.identity, provider.identity.dimensions, chunks.size),
            chunks.map { ScoredChunk(it, Quantized.quantize(provider.embedQuery(it.text))) },
        )
    }

    override fun read(ref: ArtifactRef): ByteArray = contents.getValue(ref)
    override fun absolutePath(ref: ArtifactRef): Path = error("No physical workspace")
    val reader: GraphReader = object : GraphReader {
        override fun artifacts(): List<Artifact> = artifacts.toList()
        override fun artifact(ref: ArtifactRef): Artifact? = artifacts.find { it.ref == ref }
        override fun symbol(id: SymbolId): Symbol? = symbols.find { it.id == id }
        override fun symbols(query: SymbolQuery): List<Symbol> = symbols.filter {
            query.artifact == null || it.declaration?.artifact == query.artifact
        }.take(query.limit)
        override fun structure(): WorkspaceStructure = WorkspaceStructure(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = emptyList()
        override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = emptyList()
        override fun relationships(query: RelationshipQuery): List<Relationship> = emptyList()
        override fun symbolicReferences(namespace: String?): List<SymbolicReference> = emptyList()
        override fun diagnostics(): List<Diagnostic> = emptyList()
        override fun coverage(): CoverageReport = CoverageReport(emptyList(), emptyMap(), emptyMap())
    }
}
