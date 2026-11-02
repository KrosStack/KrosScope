package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

/** The content of a graph, read once and indexed the ways the scorers look things up. */
internal class GraphIndex(reader: GraphReader) {
    val structure: WorkspaceStructure = reader.structure()
    val artifacts: List<Artifact> = reader.artifacts()
    val symbols: List<Symbol> = reader.symbols(SymbolQuery(limit = Int.MAX_VALUE))
    val relationships: List<Relationship> = reader.relationships(RelationshipQuery(limit = Int.MAX_VALUE))
    val diagnostics: List<Diagnostic> = reader.diagnostics()

    val symbolsById: Map<SymbolId, Symbol> = symbols.associateBy { it.id }
    val artifactsByPath: Map<String, Artifact> = artifacts.associateBy { it.ref.path.value }
    val symbolsByPath: Map<String, List<Symbol>> = symbols.groupBy { it.declaration?.artifact?.path?.value.orEmpty() }
    val relationshipsBySitePath: Map<String, List<Relationship>> =
        relationships.groupBy { it.site?.artifact?.path?.value.orEmpty() }
    private val constructorsByClass: Map<SymbolId?, List<Symbol>> =
        symbols.filter { it.kind.id.endsWith(CONSTRUCTOR_SUFFIX) }.groupBy { it.container }

    private val buildRoots: Map<String, BuildRoot> = structure.buildRoots.associateBy { it.id.value }
    private val sourceSetsById: Map<String, SourceSet> = structure.sourceSets.associateBy { it.id.value }
    private val targetsById = structure.targets.associateBy { it.id.value }

    val modulesByGoldPath: Map<String, Module> = structure.modules.associateBy(::goldPathOf)
    val modulesById: Map<String, Module> = structure.modules.associateBy { it.id.value }

    /** The path gold uses: a module of an included build is addressed below the directory of that build. */
    fun goldPathOf(module: Module): String {
        val directory = buildRoots[module.buildRoot.value]?.directory?.value.orEmpty()
        if (directory.isEmpty()) return module.path
        val inside = module.path.removePrefix(PATH_SEPARATOR)
        return PATH_SEPARATOR + (listOf(directory.replace('/', ':')) + listOfNotNull(inside.ifEmpty { null }))
            .joinToString(PATH_SEPARATOR)
    }

    /**
     * The primary constructor of a class, which the class header declares and so starts on the line of the class name;
     * null for anything else, including a class without an explicit primary constructor.
     */
    fun primaryConstructorOf(node: NodeRef): NodeRef? {
        val owner = (node as? NodeRef.OfSymbol)?.let { symbolsById[it.id] } ?: return null
        val headerLine = owner.nameLocation?.range?.startLine
        val primary = constructorsByClass[owner.id].orEmpty()
            .singleOrNull { it.declaration?.range?.startLine == headerLine }
        return primary?.let { NodeRef.OfSymbol(it.id) }
    }

    /** The class that declares a constructor; every other node is its own owner. */
    fun ownerOf(node: NodeRef): NodeRef {
        val symbol = (node as? NodeRef.OfSymbol)?.let { symbolsById[it.id] }
        val container = symbol?.takeIf { it.kind.id.endsWith(CONSTRUCTOR_SUFFIX) }?.container
        return container?.let { NodeRef.OfSymbol(it) } ?: node
    }

    fun isBuildLogic(module: Module): Boolean = ModuleTrait.BUILD_LOGIC in module.traits

    fun sourceSetOf(artifact: Artifact): SourceSet? = artifact.sourceSet?.let { sourceSetsById[it.value] }

    fun targetPlatformsOf(sourceSet: SourceSet) = sourceSet.targets.mapNotNull { targetsById[it.value]?.platform }

    /** True for a node the workspace owns, which must exist in the graph; libraries and externals are not. */
    fun isWorkspaceTarget(node: NodeRef): Boolean = when (node) {
        is NodeRef.OfSymbol -> ecosystemOf(node.id) == SymbolEcosystem.GRADLE
        is NodeRef.OfArtifact, is NodeRef.OfModule, is NodeRef.OfSourceSet -> true
        is NodeRef.OfExternal -> false
    }

    fun resolvedTarget(relationship: Relationship): NodeRef? = (relationship.resolution as? Resolution.Resolved)?.target

    /**
     * True for the classes that only hold generated fields: `R`, its nested classes and `BuildConfig`. Gold names the
     * field that a reference reads and not the class before it, so the references that only qualify the field are
     * not edges to score.
     */
    fun isFieldHolder(node: NodeRef): Boolean {
        val symbol = (node as? NodeRef.OfSymbol)?.let { symbolsById[it.id] } ?: return false
        val generator = (symbol.origin as? SymbolOrigin.Generated)?.generator
        return generator in fieldHolderGenerators && symbol.kind.id == JAVA_CLASS
    }

    fun exists(node: NodeRef): Boolean = when (node) {
        is NodeRef.OfSymbol -> node.id in symbolsById
        is NodeRef.OfArtifact -> node.ref.path.value in artifactsByPath
        is NodeRef.OfModule -> node.id.value in modulesById
        is NodeRef.OfSourceSet -> node.id.value in sourceSetsById
        is NodeRef.OfExternal -> true
    }

    private fun ecosystemOf(id: SymbolId): SymbolEcosystem? =
        (id.identity() as? ParsedSymbolId.Valid)?.identity?.ecosystem

    private companion object {
        val fieldHolderGenerators = setOf("android.r", "android.buildconfig")
        const val JAVA_CLASS = "java.class"
        const val PATH_SEPARATOR = ":"
        const val CONSTRUCTOR_SUFFIX = ".constructor"
    }
}
