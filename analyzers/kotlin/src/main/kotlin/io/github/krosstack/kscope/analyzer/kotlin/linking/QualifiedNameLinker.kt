package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal const val QUALIFIED_NAME_MATCH = "qualified-name-match"
internal const val COORDINATE_MATCH = "coordinate-match"

private val qualifiedName = Regex("""[A-Za-z_]\w*(\.[A-Za-z_]\w*)+""")
private val analyzerReasons = setOf("kotlin.unresolved-reference", "kotlin.unresolved-reference.no-classpath")

private class Answer(val symbol: Symbol, val subKind: String)

/**
 * Answers an unresolved import or qualified reference whose hint is a fully qualified name that exactly one workspace
 * symbol has. This happens when the build model misses a module dependency, so that the compiler cannot see a module
 * that the workspace has. The answer is an additional edge of the same kind and site; the unresolved edge stays, as
 * the compiler's own account of it. The match is textual, so it is only a heuristic.
 *
 * The symbols of the repository of the reference are considered first. Only when it has none, the symbols of other
 * repositories are, and only those declared in modules that the module of the reference depends on, which for another
 * repository means a dependency by Maven coordinates. A symbol that no dependency reaches is never an answer. The
 * repository of a reference or a symbol without a location is that of its source set.
 */
internal class QualifiedNameLinker : BoundaryLinker {
    override val descriptor =
        workspaceLinker("kscope.kotlin.qualified-name-linker", AnalyzerCapability.RESOLVED_REFERENCES)

    override fun link(graph: GraphReader, sink: FactSink) {
        val unresolved = graph.relationships(RelationshipQuery(resolved = false, limit = ALL)).mapNotNull(::hintOf)
        val candidates = unresolved.map { it.second }.distinct()
            .associateWith { graph.symbols(SymbolQuery(qualifiedName = it, limit = ALL)) }
        val reach by lazy { CoordinateReach(graph) }
        val relationships = unresolved.mapNotNull { (edge, hint) ->
            val named = candidates.getValue(hint)
            answerFor(edge, named) { reach }?.let { answer(edge, it) }
        }
        sink.acceptSorted(descriptor, relationships)
    }

    private fun hintOf(edge: Relationship): Pair<Relationship, String>? {
        val resolution = edge.resolution as? Resolution.Unresolved
        val isAnalyzerEdge = resolution != null && resolution.reason.value in analyzerReasons
        return if (resolution != null && isAnalyzerEdge && qualifiedName.matches(resolution.hint)) {
            edge to resolution.hint
        } else {
            null
        }
    }

    private fun answerFor(edge: Relationship, named: List<Symbol>, reach: () -> CoordinateReach): Answer? {
        val repository = repositoryOf(edge, reach)
        val (own, foreign) = named.partition { repositoryOf(it, reach) in setOf(null, repository) }
        return when {
            own.isNotEmpty() -> own.singleOrNull()?.let { Answer(it, QUALIFIED_NAME_MATCH) }
            foreign.isEmpty() -> null
            else -> reachable(edge, foreign, reach())?.let { Answer(it, COORDINATE_MATCH) }
        }
    }

    private fun reachable(edge: Relationship, foreign: List<Symbol>, reach: CoordinateReach): Symbol? {
        val module = reach.moduleOfReference(edge) ?: return null
        return foreign.singleOrNull { symbol ->
            reach.moduleOf(symbol.scope.sourceSet)?.let { reach.reaches(module, it) } == true
        }
    }

    private fun repositoryOf(edge: Relationship, reach: () -> CoordinateReach): RepositoryId? =
        edge.site?.artifact?.repository ?: (edge.from as? NodeRef.OfArtifact)?.ref?.repository
            ?: reach().repositoryOf(edge.scope.sourceSet)

    private fun repositoryOf(symbol: Symbol, reach: () -> CoordinateReach): RepositoryId? =
        symbol.declaration?.artifact?.repository ?: reach().repositoryOf(symbol.scope.sourceSet)

    private fun answer(unresolved: Relationship, answer: Answer) = Relationship(
        from = unresolved.from,
        kind = unresolved.kind,
        subKind = answer.subKind,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(answer.symbol.id), Certainty.HEURISTIC),
        evidenceClass = EvidenceClass.TEXTUAL,
        site = unresolved.site,
        scope = unresolved.scope,
    )
}
