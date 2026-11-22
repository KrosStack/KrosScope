package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal data class Declaration(
    val name: String,
    val kind: SymbolKind,
    val owner: SymbolId?,
    val module: String,
    val file: String,
)

internal const val REPOSITORY = "alpha"

private fun descriptorOf(declaration: Declaration, parent: SymbolDescriptor?): SymbolDescriptor {
    val base = parent ?: SymbolDescriptor()
    return when (declaration.kind.category) {
        SymbolCategory.TYPE -> base.type(declaration.name)
        SymbolCategory.CALLABLE -> base.method(declaration.name)
        else -> base.term(declaration.name)
    }
}

private fun artifactAt(repositories: Map<SymbolId, String>, id: SymbolId, path: String) =
    ArtifactRef(RepositoryId(repositories[id] ?: REPOSITORY), RepoPath(path))

private fun symbolOf(id: SymbolId, declaration: Declaration, qualifiedName: String, home: ArtifactRef): Symbol = Symbol(
    id = id,
    kind = declaration.kind,
    name = declaration.name,
    qualifiedName = qualifiedName,
    signature = if (declaration.kind.category == SymbolCategory.CALLABLE) "fun ${declaration.name}()" else null,
    container = declaration.owner,
    visibility = Visibility.PUBLIC,
    flags = emptySet(),
    origin = SymbolOrigin.Source,
    declaration = SourceLocation(home, TextRange(1, 1, 2, 1)),
    nameLocation = null,
    documentation = null,
    scope = Scope(),
    evidenceClass = EvidenceClass.COMPILER,
    certainty = Certainty.EXACT,
)

internal class SymbolWorld {
    val symbols = LinkedHashMap<SymbolId, Symbol>()
    val relationships = mutableListOf<Relationship>()
    private val descriptors = HashMap<SymbolId, SymbolDescriptor>()
    val modules = HashMap<SymbolId, String>()
    val files = HashMap<SymbolId, String>()
    private val repositoryOf = HashMap<SymbolId, String>()
    val directories = linkedMapOf(REPOSITORY to "")

    fun type(name: String, owner: SymbolId? = null, module: String = "app", kind: String = "kotlin.class"): SymbolId =
        declare(Declaration(name, SymbolKind(kind, SymbolCategory.TYPE), owner, module, "src/main/$name.kt"))

    fun function(
        name: String,
        owner: SymbolId? = null,
        file: String = "src/main/Top.kt",
        repository: String = REPOSITORY,
    ): SymbolId {
        val kind = SymbolKind("kotlin.function", SymbolCategory.CALLABLE)
        return declare(Declaration(name, kind, owner, "app", file), repository)
    }

    fun property(name: String, owner: SymbolId): SymbolId {
        val kind = SymbolKind("kotlin.property", SymbolCategory.VARIABLE)
        return declare(Declaration(name, kind, owner, "app", "src/main/Top.kt"))
    }

    fun call(from: SymbolId, to: SymbolId, line: Int, certainty: Certainty = Certainty.EXACT) {
        relationships += relation(from, RelationKind.CALLS, Resolution.Resolved(NodeRef.OfSymbol(to), certainty), line)
    }

    fun unresolvedCall(from: SymbolId, line: Int, reason: String = "kotlin.unresolved-reference") {
        relationships += relation(from, RelationKind.CALLS, Resolution.Unresolved("missing", ReasonCode(reason)), line)
    }

    fun notAnalysedCall(from: SymbolId, line: Int, reason: String = "jni.native-side-not-analysed") {
        relationships += relation(from, RelationKind.CALLS, Resolution.NotAnalysed("native", ReasonCode(reason)), line)
    }

    fun relate(from: SymbolId, kind: RelationKind, to: SymbolId, certainty: Certainty = Certainty.EXACT) {
        relationships += relation(from, kind, Resolution.Resolved(NodeRef.OfSymbol(to), certainty), null)
    }

    fun structure(): WorkspaceStructure {
        val home = RepoPath(directories.getValue(REPOSITORY))
        val repository = Repository(RepositoryId(REPOSITORY), REPOSITORY, null, null, home)
        val root = BuildRoot(BuildRootId("$REPOSITORY//"), repository.id, RepoPath(""), BuildSystem.GRADLE)
        val moduleList = modules.values.distinct().sorted().map { name ->
            Module(
                ModuleId("$REPOSITORY//:$name"), root.id, ":$name", RepoPath(name), emptyList(), emptySet(),
                null, EvidenceClass.BUILD_MODEL, Certainty.EXACT,
            )
        }
        val others = directories.filterKeys { it != REPOSITORY }
            .map { (id, directory) -> Repository(RepositoryId(id), id, null, null, RepoPath(directory)) }
        val repositories = listOf(repository) + others
        return WorkspaceStructure(repositories, listOf(root), moduleList, emptyList(), emptyList(), emptyList())
    }

    private fun declare(declaration: Declaration, repository: String = REPOSITORY): SymbolId {
        val owner = declaration.owner
        val home = owner?.let { modules.getValue(it) } ?: declaration.module
        val path = owner?.let { files.getValue(it) } ?: declaration.file
        val descriptor = descriptorOf(declaration, declaration.owner?.let { descriptors.getValue(it) })
        val id = SymbolIdentity.workspace(ModuleId("$REPOSITORY//:$home"), "main", descriptor).toSymbolId()
        descriptors[id] = descriptor
        val qualifier = owner?.let { symbols.getValue(it).qualifiedName } ?: home
        val qualifiedName = "$qualifier.${declaration.name}"
        repositoryOf[id] = repository
        return enroll(id, declaration.copy(module = home, file = path), qualifiedName)
    }

    fun enroll(id: SymbolId, declaration: Declaration, qualifiedName: String): SymbolId {
        modules[id] = declaration.module
        files[id] = declaration.file
        symbols[id] = symbolOf(id, declaration, qualifiedName, artifactAt(repositoryOf, id, declaration.file))
        return id
    }

    private fun relation(from: SymbolId, kind: RelationKind, resolution: Resolution, line: Int?): Relationship {
        val site = artifactAt(repositoryOf, from, files.getValue(from))
        return Relationship(
            NodeRef.OfSymbol(from),
            kind,
            null,
            resolution,
            EvidenceClass.COMPILER,
            line?.let { SourceLocation(site, TextRange(it, 5, it, 20)) },
            Scope(),
        )
    }
}
