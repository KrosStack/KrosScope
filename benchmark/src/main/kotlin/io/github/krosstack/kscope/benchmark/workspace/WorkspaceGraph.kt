package io.github.krosstack.kscope.benchmark.workspace

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery

/** A graph read the ways the link producers need: places named with their repository directory, and modules. */
internal class WorkspaceGraph(val reader: GraphReader) {
    private val structure = reader.structure()
    private val directories = structure.repositories.associate { it.id to it.directory.value }
    private val modulesById = structure.modules.associateBy { it.id.value }
    private val repositoriesByBuildRoot = structure.buildRoots.associate { it.id to it.repository }
    private val modulesBySourceSet = structure.sourceSets.associate { it.id to it.module }

    val relationships: List<Relationship> = reader.relationships(RelationshipQuery(limit = Int.MAX_VALUE))

    fun relationships(kind: RelationKind, subKind: String?): List<Relationship> =
        relationships.filter { it.kind == kind && it.subKind == subKind }

    fun target(relationship: Relationship): NodeRef? = (relationship.resolution as? Resolution.Resolved)?.target

    fun symbol(node: NodeRef?): Symbol? = (node as? NodeRef.OfSymbol)?.let { reader.symbol(it.id) }

    fun placeOf(location: SourceLocation): Place {
        val directory = directories.getValue(location.artifact.repository)
        return Place("$directory/${location.artifact.path.value}", location.range.startLine)
    }

    fun at(symbol: Symbol?): LinkEnd.At? {
        val location = symbol?.let { it.nameLocation ?: it.declaration } ?: return null
        return LinkEnd.At(placeOf(location))
    }

    fun moduleEnd(node: NodeRef?): LinkEnd.OfModule? =
        (node as? NodeRef.OfModule)?.let { modulesById[it.id.value] }?.let(::moduleEnd)

    fun moduleEnd(module: Module): LinkEnd.OfModule {
        val repository = repositoriesByBuildRoot.getValue(module.buildRoot)
        return LinkEnd.OfModule(directories.getValue(repository), module.path)
    }

    fun moduleEndOf(symbol: Symbol): LinkEnd.OfModule? =
        (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit?.let(modulesById::get)?.let(::moduleEnd)

    fun moduleEndOf(artifact: ArtifactRef): LinkEnd.OfModule? = reader.artifact(artifact)?.sourceSet
        ?.let(modulesBySourceSet::get)?.let { modulesById[it.value] }?.let(::moduleEnd)
}

internal fun EvidenceClass.wireName(): String = EvidenceClass.serializer().descriptor.getElementName(ordinal)
