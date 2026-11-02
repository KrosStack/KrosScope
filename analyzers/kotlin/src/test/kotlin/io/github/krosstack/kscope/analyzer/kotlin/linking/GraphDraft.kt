package io.github.krosstack.kscope.analyzer.kotlin.linking

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
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.VariantId
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

private val kotlinSource = ArtifactType("kotlin.source", ArtifactCategory.SOURCE)

/** The parts of a source set that differ from one test to the next. */
internal data class SetShape(
    val role: SourceSetRole = SourceSetRole.MAIN,
    val dependsOn: List<SourceSetId> = emptyList(),
    val variants: List<String> = emptyList(),
    val targets: List<String> = emptyList(),
    val certainty: Certainty = Certainty.EXACT,
    val repository: String = "t",
)

/** The parts of a symbol that differ from one test to the next. */
private data class Shape(
    val kindId: String,
    val category: SymbolCategory,
    val flags: Set<SymbolFlag>,
    val container: Declared?,
    val origin: SymbolOrigin = SymbolOrigin.Source,
)

/** A symbol of a draft, with the places tests ask about. */
internal class Declared(val symbol: Symbol, val sourceSet: SourceSetId) {
    val id get() = symbol.id
    val node: NodeRef get() = NodeRef.OfSymbol(symbol.id)
}

/** A workspace that tests build by hand: source sets, symbols and relationships, read back as a graph. */
internal class GraphDraft {
    private val sourceSets = mutableListOf<SourceSet>()
    private val symbols = mutableListOf<Symbol>()
    private val relationships = mutableListOf<Relationship>()
    private val artifacts = mutableMapOf<String, Artifact>()
    private val repositories = mutableMapOf<SourceSetId, RepositoryId>()
    private val targets = mutableListOf<Target>()

    fun target(module: String, name: String, platform: Platform) {
        targets += Target(TargetId("t//$module#target/$name"), ModuleId("t//$module"), name, platform)
    }

    fun sourceSet(module: String, name: String, shape: SetShape = SetShape()): SourceSetId {
        val id = sourceSetId(module, name)
        repositories[id] = RepositoryId(shape.repository)
        sourceSets += SourceSet(
            id = id,
            module = ModuleId("t//$module"),
            name = name,
            role = shape.role,
            dependsOn = shape.dependsOn,
            roots = emptyList(),
            targets = shape.targets.map { TargetId("t//$module#target/$it") },
            variants = shape.variants.map { VariantId("t//$module#variant/$it") },
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = shape.certainty,
        )
        return id
    }

    fun dependency(from: String, to: String) {
        relationships += Relationship(
            from = NodeRef.OfModule(ModuleId("t//$from")),
            kind = RelationKind.DEPENDS_ON,
            subKind = null,
            resolution = Resolution.Resolved(NodeRef.OfModule(ModuleId("t//$to")), Certainty.EXACT),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = Scope(),
        )
    }

    fun type(
        sourceSet: SourceSetId,
        qualifiedName: String,
        flags: Set<SymbolFlag> = emptySet(),
        container: Declared? = null,
        origin: SymbolOrigin = SymbolOrigin.Source,
    ): Declared {
        val shape = Shape("kotlin.class", SymbolCategory.TYPE, flags, container, origin)
        return declare(sourceSet, qualifiedName, shape)
    }

    fun function(
        sourceSet: SourceSetId,
        qualifiedName: String,
        flags: Set<SymbolFlag> = emptySet(),
        container: Declared? = null,
    ): Declared {
        val shape = Shape("kotlin.function", SymbolCategory.CALLABLE, flags, container)
        return declare(sourceSet, qualifiedName, shape)
    }

    fun typeWithoutLocation(sourceSet: SourceSetId, qualifiedName: String): Declared {
        val symbol = type(sourceSet, qualifiedName).symbol.copy(declaration = null, nameLocation = null)
        symbols[symbols.lastIndex] = symbol
        return Declared(symbol, sourceSet)
    }

    private fun declare(sourceSet: SourceSetId, qualifiedName: String, shape: Shape): Declared {
        val module = sourceSets.first { it.id == sourceSet }.module
        val segments = qualifiedName.split('.')
        val name = segments.last()
        val descriptor = descriptorOf(segments, shape.category)
        val id = SymbolIdentity.workspace(module, sourceSet.value.substringAfter('@'), descriptor).toSymbolId()
        val directory = module.value.removePrefix("t//:").replace(':', '/')
        val path = "$directory/src/${sourceSet.value.substringAfter('@')}/${qualifiedName.replace('.', '/')}.kt"
        val ref = artifactFor(path, sourceSet)
        val symbol = Symbol(
            id = id,
            kind = SymbolKind(shape.kindId, shape.category),
            name = name,
            qualifiedName = qualifiedName,
            signature = null,
            container = shape.container?.id,
            visibility = Visibility.PUBLIC,
            flags = shape.flags,
            origin = shape.origin,
            declaration = SourceLocation(ref, TextRange(3, 1, 9, 2)),
            nameLocation = SourceLocation(ref, TextRange(3, 7, 3, 7 + name.length)),
            documentation = null,
            scope = Scope(sourceSet = sourceSet),
            evidenceClass = EvidenceClass.COMPILER,
            certainty = Certainty.EXACT,
        )
        symbols += symbol
        return Declared(symbol, sourceSet)
    }

    private fun artifactFor(path: String, sourceSet: SourceSetId): ArtifactRef {
        val ref = ArtifactRef(repositories.getValue(sourceSet), RepoPath(path))
        artifacts.getOrPut(path) {
            Artifact(ref, kotlinSource, ContentHash("0"), 1, 10, sourceSet, null)
        }
        return ref
    }

    fun relate(from: Declared, kind: RelationKind, to: Declared, evidence: EvidenceClass = EvidenceClass.COMPILER) {
        relationships += Relationship(
            from = from.node,
            kind = kind,
            subKind = null,
            resolution = Resolution.Resolved(to.node, Certainty.EXACT),
            evidenceClass = evidence,
            site = from.symbol.nameLocation,
            scope = from.symbol.scope,
        )
    }

    fun unresolved(from: Declared, kind: RelationKind, hint: String, reason: String = UNRESOLVED) {
        relationships += Relationship(
            from = from.symbol.declaration?.let { NodeRef.OfArtifact(it.artifact) } ?: from.node,
            kind = kind,
            subKind = null,
            resolution = Resolution.Unresolved(hint, ReasonCode(reason)),
            evidenceClass = EvidenceClass.COMPILER,
            site = from.symbol.declaration,
            scope = from.symbol.scope,
        )
    }

    fun graph(): DraftGraph = DraftGraph(
        structureOf(sourceSets, repositories, targets),
        symbols.toList(),
        relationships.toList(),
        artifacts.values.toList(),
    )

    companion object {
        const val UNRESOLVED = "kotlin.unresolved-reference.no-classpath"

        fun sourceSetId(module: String, name: String) = SourceSetId("t//$module@$name")
    }
}

private fun descriptorOf(segments: List<String>, category: SymbolCategory): SymbolDescriptor {
    val packages = segments.dropLast(1).fold(SymbolDescriptor()) { descriptor, name -> descriptor.namespace(name) }
    val name = segments.last()
    return if (category == SymbolCategory.TYPE) packages.type(name) else packages.method(name, "")
}

private fun structureOf(
    sourceSets: List<SourceSet>,
    repositories: Map<SourceSetId, RepositoryId>,
    targets: List<Target>,
): WorkspaceStructure {
    val repositoryOfModule = sourceSets.associate { it.module to repositories.getValue(it.id) }
    val buildRoots = repositoryOfModule.values.distinct().map {
        BuildRoot(BuildRootId("${it.value}//"), it, RepoPath(""), BuildSystem.GRADLE)
    }
    val modules = repositoryOfModule.map { (id, repository) ->
        Module(
            id = id,
            buildRoot = BuildRootId("${repository.value}//"),
            path = id.value.removePrefix("t//"),
            directory = RepoPath(""),
            plugins = emptyList(),
            traits = emptySet(),
            androidNamespace = null,
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
        )
    }
    return WorkspaceStructure(emptyList(), buildRoots, modules, targets, emptyList(), sourceSets)
}

/** The relationships a linker added to a draft, as the engine would store them. */
internal fun BoundaryLinker.linkedFrom(draft: GraphDraft): List<Relationship> {
    val facts = mutableListOf<Fact>()
    link(draft.graph(), FactSink { batch: FactBatch -> facts += batch.facts })
    return facts.filterIsInstance<Fact.OfRelationship>().map { it.value }
}
