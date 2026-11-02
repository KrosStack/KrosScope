package io.github.krosstack.kscope.benchmark.score.contract

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.FakeGraph
import io.github.krosstack.kscope.benchmark.score.Graphs
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange

/** Building blocks for the tests of the scorers of schema artifacts: symbols, relationships and gold relationships. */
internal fun Symbol.ofKind(id: String, category: SymbolCategory = SymbolCategory.SCHEMA) =
    copy(kind = SymbolKind(id, category))

internal fun Symbol.madeBy(generator: String, container: Symbol? = null) = copy(
    declaration = null,
    nameLocation = null,
    origin = SymbolOrigin.Generated(generator, null),
    container = container?.id,
)

internal fun schemaEdge(
    from: Symbol,
    to: Symbol,
    kind: RelationKind,
    evidence: EvidenceClass,
    at: Pair<String, Int>? = null,
) = Relationship(
    from = NodeRef.OfSymbol(from.id),
    kind = kind,
    subKind = null,
    resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT),
    evidenceClass = evidence,
    site = at?.let { (path, line) -> SourceLocation(Graphs.ref(path), TextRange(line, 3, line, 20)) },
    scope = Scope(),
)

internal fun schemaGold(id: String, relationshipClass: String, from: GoldAddress, to: GoldAddress, evidence: String) =
    GoldRelationship(id, relationshipClass, from, to, evidence)

internal fun scoringOf(graph: FakeGraph): Pair<GraphIndex, GoldAddressResolver> =
    GraphIndex(graph) to GoldAddressResolver(graph)

internal fun coreGraph(symbols: List<Symbol>, files: List<String>, relationships: List<Relationship>) = FakeGraph(
    symbols = symbols,
    artifacts = files.map { Graphs.artifact(it) },
    modules = listOf(Graphs.module(":core", "core", Graphs.buildRoot("").id)),
    buildRoots = listOf(Graphs.buildRoot("")),
    relationships = relationships,
)
