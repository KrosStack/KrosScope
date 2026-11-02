package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private data class ActualizingPair(val actual: SymbolId, val expect: SymbolId)

/**
 * Binds an `actual` declaration to the `expect` declaration of the same descriptor in a source set it depends on, for
 * the pairs the compiler did not bind. The compiler binds the source sets it analyses with the actual's platform
 * front end; those of a target it could not analyse, such as a native one, have no such edge. The edge is a
 * convention, so it is only probable.
 */
internal class ExpectActualConventionLinker : BoundaryLinker {
    override val descriptor = workspaceLinker("kscope.kotlin.expect-actual-linker", AnalyzerCapability.INHERITANCE)

    override fun link(graph: GraphReader, sink: FactSink) {
        val sourceSets = SourceSetModel(graph.structure().sourceSets)
        val expects = graph.symbols(SymbolQuery(flag = SymbolFlag.EXPECT, limit = ALL))
            .filter { it.declarationKey() != null }.groupBy { it.declarationKey() }
        val bound = boundPairs(graph)
        val relationships = graph.symbols(SymbolQuery(flag = SymbolFlag.ACTUAL, limit = ALL)).flatMap { actual ->
            expects[actual.declarationKey()].orEmpty()
                .filter { expect -> isExpectOf(actual, expect, sourceSets) }
                .filter { expect -> ActualizingPair(actual.id, expect.id) !in bound }
                .map { expect -> actualizes(actual, expect) }
        }
        sink.acceptSorted(descriptor, relationships)
    }

    private fun isExpectOf(actual: Symbol, expect: Symbol, sourceSets: SourceSetModel): Boolean {
        val actualSet = actual.scope.sourceSet
        val expectSet = expect.scope.sourceSet
        return actualSet != null && expectSet != null && sourceSets.dependsOn(actualSet, expectSet)
    }

    private fun boundPairs(graph: GraphReader): Set<ActualizingPair> = graph.relationships(
        RelationshipQuery(kinds = setOf(RelationKind.ACTUALIZES), limit = ALL),
    ).mapNotNull { edge ->
        val actual = (edge.from as? NodeRef.OfSymbol)?.id
        val expect = ((edge.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id
        if (actual != null && expect != null) ActualizingPair(actual, expect) else null
    }.toSet()

    private fun actualizes(actual: Symbol, expect: Symbol) = Relationship(
        from = NodeRef.OfSymbol(actual.id),
        kind = RelationKind.ACTUALIZES,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(expect.id), Certainty.PROBABLE),
        evidenceClass = EvidenceClass.CONVENTION,
        site = actual.nameLocation ?: actual.declaration,
        scope = actual.scope,
    )
}
