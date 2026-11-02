package io.github.krosstack.kscope.benchmark.score

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
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.Visibility

internal object Graphs {
    val repository = RepositoryId("repo")
    val moduleId = ModuleId("repo//:core")
    val sourceSetId = SourceSetId("repo//:core@main")

    fun ref(path: String) = ArtifactRef(repository, RepoPath(path))

    fun artifact(path: String, type: String = "kotlin.source") = Artifact(
        ref = ref(path),
        type = ArtifactType(type, ArtifactCategory.SOURCE),
        contentHash = ContentHash("0".repeat(HASH_LENGTH)),
        sizeBytes = 1,
        lineCount = LINES,
        sourceSet = sourceSetId,
        generated = null,
    )

    /** [descriptor] is built by the caller; [line] is the line of the name and of the whole declaration. */
    fun symbol(
        path: String,
        qualifiedName: String,
        kindId: String,
        line: Int,
        descriptor: SymbolDescriptor = SymbolDescriptor().term(qualifiedName),
    ): Symbol {
        val location = SourceLocation(ref(path), TextRange(line, 1, line, 2))
        return Symbol(
            id = SymbolIdentity.workspace(moduleId, "main", descriptor).toSymbolId(),
            kind = SymbolKind(kindId, SymbolCategory.TYPE),
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            signature = null,
            container = null,
            visibility = Visibility.PUBLIC,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = location,
            nameLocation = location,
            documentation = null,
            scope = Scope(sourceSet = sourceSetId),
            evidenceClass = EvidenceClass.STRUCTURAL,
            certainty = Certainty.EXACT,
        )
    }

    fun module(path: String, directory: String, buildRoot: BuildRootId, traits: Set<ModuleTrait> = emptySet()) = Module(
        id = ModuleId("${buildRoot.value}$path"),
        buildRoot = buildRoot,
        path = path,
        directory = RepoPath(directory),
        plugins = emptyList(),
        traits = traits,
        androidNamespace = null,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    fun buildRoot(directory: String) =
        BuildRoot(BuildRootId("repo//$directory"), repository, RepoPath(directory), BuildSystem.GRADLE)

    fun call(from: Symbol, to: Symbol, path: String, line: Int, kind: RelationKind = RelationKind.CALLS) = Relationship(
        from = NodeRef.OfSymbol(from.id),
        kind = kind,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT),
        evidenceClass = EvidenceClass.COMPILER,
        site = SourceLocation(ref(path), TextRange(line, 5, line, 9)),
        scope = Scope(sourceSet = sourceSetId),
    )

    fun type(name: String) = SymbolDescriptor().namespace("p").type(name)

    fun method(type: String, name: String, parameters: String) = type(type).method(name, parameters)

    private const val HASH_LENGTH = 64
    private const val LINES = 100
}
