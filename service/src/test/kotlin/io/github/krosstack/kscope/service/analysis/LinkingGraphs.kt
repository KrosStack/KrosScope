package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/** What the tests on the Kotlin linkers read from the graph a scan made. */
internal fun GraphReader.edges(kind: RelationKind, subKind: String? = null): List<Relationship> =
    relationships(RelationshipQuery(kinds = setOf(kind), subKind = subKind, limit = Int.MAX_VALUE))

internal fun GraphReader.classNamed(qualifiedName: String): Symbol =
    symbols(SymbolQuery(qualifiedName = qualifiedName)).first { it.kind.category.name == "TYPE" }

internal fun GraphReader.testedBy(qualifiedName: String): Relationship? {
    val test = classNamed(qualifiedName)
    return edges(RelationKind.TESTS).singleOrNull { it.from == NodeRef.OfSymbol(test.id) }
}

internal fun GraphReader.subjectOf(edge: Relationship?): String? {
    val target = (edge?.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol
    return target?.let { symbol(it.id)?.qualifiedName }
}
