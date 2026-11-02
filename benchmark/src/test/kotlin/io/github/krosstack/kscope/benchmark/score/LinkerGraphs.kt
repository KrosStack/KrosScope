package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.TextRange

/** A resolved relationship between two symbols, as the tests of the Kotlin linker scorers build graphs from. */
internal fun edge(
    from: Symbol,
    kind: RelationKind,
    to: Symbol,
    evidence: EvidenceClass,
    site: SourceLocation? = null,
): Relationship {
    val resolution = Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT)
    return Relationship(NodeRef.OfSymbol(from.id), kind, null, resolution, evidence, site, Scope())
}

internal fun at(path: String, line: Int) = SourceLocation(Graphs.ref(path), TextRange(line, 5, line, 9))

internal fun goldOf(id: String, relationshipClass: String, from: GoldAddress, to: GoldAddress, evidence: String) =
    GoldRelationship(id, relationshipClass, from, to, evidence)

internal fun ClassScore.misses() = score?.misses.orEmpty()

internal fun ClassScore.extras() = score?.extras.orEmpty()
