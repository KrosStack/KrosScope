package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
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
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal val repo = RepositoryId("repo")

private val buildRoot = BuildRoot(BuildRootId("repo//"), repo, RepoPath(""), BuildSystem.GRADLE)

internal fun moduleOf(path: String, directory: String) = Module(
    ModuleId("repo//$path"),
    buildRoot.id,
    path,
    RepoPath(directory),
    emptyList(),
    setOf(ModuleTrait.KOTLIN_JVM),
    null,
    EvidenceClass.BUILD_MODEL,
    Certainty.EXACT,
)

internal fun sourceSetOf(module: Module, name: String) = SourceSet(
    SourceSetId("${module.id.value}@$name"),
    module.id,
    name,
    SourceSetRole.MAIN,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    EvidenceClass.BUILD_MODEL,
    Certainty.EXACT,
)

internal fun structureOf(modules: List<Module>, sourceSets: List<SourceSet>) = WorkspaceStructure(
    listOf(Repository(repo, "repo", null, null)),
    listOf(buildRoot),
    modules,
    emptyList(),
    emptyList(),
    sourceSets,
)

internal fun artifactAt(path: String, sourceSet: SourceSet? = null, lines: Int = 10) = Artifact(
    ArtifactRef(repo, RepoPath(path)),
    ArtifactType("kotlin.source", ArtifactCategory.SOURCE),
    ContentHash("0123456789abcdef0123456789abcdef"),
    100,
    lines,
    sourceSet?.id,
    null,
)

internal fun fileOf(path: String): NodeRef = NodeRef.OfArtifact(ArtifactRef(repo, RepoPath(path)))

internal fun symbolOf(
    id: String,
    path: String? = null,
    line: Int = 1,
    origin: SymbolOrigin = SymbolOrigin.Source,
    container: String? = null,
) = Symbol(
    SymbolId(id),
    SymbolKind("kotlin.class", SymbolCategory.TYPE),
    id.substringAfterLast('.'),
    id,
    null,
    container?.let(::SymbolId),
    Visibility.PUBLIC,
    emptySet(),
    origin,
    path?.let { SourceLocation(ArtifactRef(repo, RepoPath(it)), TextRange(line, 1, line + 1, 1)) },
    null,
    null,
    Scope(),
    EvidenceClass.COMPILER,
    Certainty.EXACT,
)

internal fun edge(
    from: NodeRef,
    to: NodeRef,
    kind: RelationKind = RelationKind.CALLS,
    certainty: Certainty = Certainty.EXACT,
) = Relationship(from, kind, null, Resolution.Resolved(to, certainty), EvidenceClass.COMPILER, null, Scope())

internal fun unresolvedFrom(from: NodeRef) = Relationship(
    from,
    RelationKind.REFERENCES,
    null,
    Resolution.Unresolved("Missing", ReasonCode("kotlin.unresolved-reference")),
    EvidenceClass.COMPILER,
    null,
    Scope(),
)

internal fun notAnalysedFrom(from: NodeRef) = Relationship(
    from,
    RelationKind.BINDS,
    null,
    Resolution.NotAnalysed("Native side", ReasonCode("jni.native-side-not-analysed")),
    EvidenceClass.STRUCTURAL,
    null,
    Scope(),
)

internal fun sym(id: String): NodeRef = NodeRef.OfSymbol(SymbolId(id))
