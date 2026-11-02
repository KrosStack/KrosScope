package io.github.krosstack.kscope.context

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
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.graph.CoverageReport
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

internal class ContextFixture private constructor(
    val inventory: List<Artifact>,
    val declarations: List<Symbol>,
    val edges: List<Relationship>,
    private val contentFiles: ContextFiles,
) : GraphReader,
    WorkspaceFiles by contentFiles {
    constructor(
        inventory: List<Artifact>,
        declarations: List<Symbol>,
        content: Map<ArtifactRef, String>,
        edges: List<Relationship> = emptyList(),
    ) : this(inventory, declarations, edges, ContextFiles(content))

    val reads: Map<ArtifactRef, Int> get() = contentFiles.reads

    val content: Map<ArtifactRef, String> get() = contentFiles.content

    override fun structure(): WorkspaceStructure = WorkspaceStructure(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )

    override fun artifacts(): List<Artifact> = inventory

    override fun artifact(ref: ArtifactRef): Artifact? = inventory.find { it.ref == ref }

    override fun symbol(id: SymbolId): Symbol? = declarations.find { it.id == id }

    override fun symbols(query: SymbolQuery): List<Symbol> = declarations
        .filter { query.artifact == null || it.declaration?.artifact == query.artifact }.take(query.limit)

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = edges.filter {
        it.from == node && (kinds.isEmpty() || it.kind in kinds)
    }

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> = edges.filter {
        (it.resolution as? Resolution.Resolved)?.target == node && (kinds.isEmpty() || it.kind in kinds)
    }

    override fun relationships(query: RelationshipQuery): List<Relationship> = edges.take(query.limit)

    override fun symbolicReferences(namespace: String?): List<SymbolicReference> = emptyList()

    override fun diagnostics(): List<Diagnostic> = emptyList()

    override fun coverage(): CoverageReport = CoverageReport(emptyList(), emptyMap(), emptyMap())
}

private class ContextFiles(val content: Map<ArtifactRef, String>) : WorkspaceFiles {
    val reads: MutableMap<ArtifactRef, Int> = mutableMapOf()

    override fun read(ref: ArtifactRef): ByteArray {
        reads.merge(ref, 1, Int::plus)
        return content.getValue(ref).toByteArray(Charsets.UTF_8)
    }

    override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
}

internal fun ContextFixture.withSignaturesReworded(): ContextFixture = ContextFixture(
    inventory,
    declarations.map { it.copy(signature = "${it.signature} reworded") },
    content,
    edges,
)

internal fun sourceArtifact(path: String, text: String): Artifact = Artifact(
    ArtifactRef(RepositoryId("sample"), RepoPath(path)),
    ArtifactType("test.source", ArtifactCategory.SOURCE),
    sourceHash(text),
    text.toByteArray(Charsets.UTF_8).size.toLong(),
    text.lines().size,
    null,
    null,
)

private fun sourceHash(text: String): ContentHash {
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return ContentHash(HexFormat.of().formatHex(digest))
}

internal fun declaration(name: String, file: Artifact, range: TextRange, signature: String): Symbol = Symbol(
    id = SymbolId("kscope test module main $name()."),
    kind = SymbolKind("test.function", SymbolCategory.CALLABLE),
    name = name,
    qualifiedName = name,
    signature = signature,
    container = null,
    visibility = Visibility.PUBLIC,
    flags = emptySet(),
    origin = SymbolOrigin.Source,
    declaration = SourceLocation(file.ref, range),
    nameLocation = SourceLocation(file.ref, range),
    documentation = null,
    scope = Scope(),
    evidenceClass = EvidenceClass.COMPILER,
    certainty = Certainty.EXACT,
)
