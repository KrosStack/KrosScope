package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reading helpers for the integration tests of the schema samples. */
internal fun GraphReader.allRelationships(): List<Relationship> =
    relationships(RelationshipQuery(limit = Int.MAX_VALUE))

internal fun GraphReader.symbolOf(relationship: Relationship): Symbol? =
    ((relationship.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.let { symbol(it.id) }

internal fun GraphReader.generated(qualifiedName: String): List<Symbol> =
    symbols(SymbolQuery(qualifiedName = qualifiedName, limit = Int.MAX_VALUE))
        .filter { it.origin is SymbolOrigin.Generated }

internal fun GraphReader.declared(qualifiedName: String, kindId: String): Symbol =
    symbols(SymbolQuery(qualifiedName = qualifiedName, kindId = kindId, limit = Int.MAX_VALUE)).single()

/** The relationships from the code of [file] to the symbol named [qualifiedName]. */
internal fun GraphReader.edgesInto(file: String, qualifiedName: String): List<Relationship> =
    allRelationships().filter { it.site?.artifact?.path?.value == file && symbolOf(it)?.qualifiedName == qualifiedName }

internal fun assertCompilerExact(edges: List<Relationship>, what: String) {
    assertTrue(edges.isNotEmpty(), "No edge to $what")
    edges.forEach {
        assertEquals(EvidenceClass.COMPILER, it.evidenceClass, what)
        assertEquals(Certainty.EXACT, (it.resolution as Resolution.Resolved).certainty, what)
    }
}

internal fun GraphReader.generatesEdges(source: Symbol): List<Relationship> =
    outgoing(NodeRef.OfSymbol(source.id), setOf(RelationKind.GENERATES))

/** The names of the workspace symbols that resolved relationships name and the graph does not have. */
internal fun GraphReader.danglingTargets(): List<String> {
    val targets = allRelationships().mapNotNull { (it.resolution as? Resolution.Resolved)?.target }
        .filterIsInstance<NodeRef.OfSymbol>().map { it.id }
        .filter { (it.identity() as? ParsedSymbolId.Valid)?.identity?.ecosystem == SymbolEcosystem.GRADLE }
    return targets.filter { symbol(it) == null }.map { it.value }.distinct()
}
