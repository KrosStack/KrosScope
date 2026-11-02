package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal const val VARIANT_ALTERNATIVE = "variant-alternative"

private data class SourceSetPair(val first: SourceSetId, val second: SourceSetId)

/**
 * Binds the declarations that a module spells once per build variant, such as a `Branding` object in the `free` and
 * in the `paid` source set. Only one of them is part of any build, so the compiler sees one and the other is reached
 * through the edge: it joins declarations of one descriptor in source sets that share no variant and are not related
 * by `dependsOn`. One edge is emitted for each unordered pair, from the declaration of the source set that sorts first.
 * The link follows from the variant model of the build, so the edge carries build-model evidence, exact when both
 * source sets are exact in that model and heuristic otherwise.
 */
internal class VariantAlternativeLinker : BoundaryLinker {
    override val descriptor =
        workspaceLinker("kscope.kotlin.variant-alternative-linker", AnalyzerCapability.INHERITANCE)

    override fun link(graph: GraphReader, sink: FactSink) {
        val structure = graph.structure()
        val sourceSets = SourceSetModel(structure.sourceSets)
        val perModule = structure.sourceSets.groupBy { it.module }.values
        val alternatives = perModule.flatMap { alternativePairs(it, sourceSets) }
        val involved = alternatives.flatMap { listOf(it.first, it.second) }.toSet()
        val relationships = declarationsIn(graph, involved).groupBy { it.declarationKey() }.values
            .flatMap { sameDeclaration -> pairsOf(sameDeclaration, alternatives) }
            .map { binds(it, sourceSets) }
        sink.acceptSorted(descriptor, relationships)
    }

    private fun alternativePairs(moduleSets: List<SourceSet>, sourceSets: SourceSetModel): List<SourceSetPair> {
        val ids = moduleSets.map { it.id }.sortedBy { it.value }
        return ids.flatMapIndexed { index, first ->
            ids.drop(index + 1).filter { second -> sourceSets.areAlternatives(first, second) }
                .map { SourceSetPair(first, it) }
        }
    }

    private fun declarationsIn(graph: GraphReader, sourceSetIds: Set<SourceSetId>): List<Symbol> {
        val artifacts = graph.artifacts().filter { it.sourceSet in sourceSetIds }
        return artifacts.flatMap { graph.symbols(SymbolQuery(artifact = it.ref, limit = ALL)) }
            .filter { it.scope.sourceSet in sourceSetIds && it.isFromSource() && it.declarationKey() != null }
            .filter { it.kind.category != SymbolCategory.PACKAGE }
            .distinctBy { it.id }
    }

    private fun pairsOf(declarations: List<Symbol>, alternatives: List<SourceSetPair>): List<Pair<Symbol, Symbol>> {
        val bySourceSet = declarations.associateBy { it.scope.sourceSet }
        return alternatives.mapNotNull { (first, second) ->
            val from = bySourceSet[first]
            val to = bySourceSet[second]
            if (from != null && to != null) from to to else null
        }
    }

    private fun binds(pair: Pair<Symbol, Symbol>, sourceSets: SourceSetModel): Relationship {
        val (from, to) = pair
        val isModelExact = listOf(from, to).all { sourceSets.isExact(it.scope.sourceSet) }
        return Relationship(
            from = NodeRef.OfSymbol(from.id),
            kind = RelationKind.BINDS,
            subKind = VARIANT_ALTERNATIVE,
            resolution = Resolution.Resolved(
                NodeRef.OfSymbol(to.id),
                if (isModelExact) Certainty.EXACT else Certainty.HEURISTIC,
            ),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = from.scope,
        )
    }
}
