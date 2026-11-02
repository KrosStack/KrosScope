package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange

internal object Builders {
    val producer = ProducerRef("test-producer", "1")

    fun artifactRef(path: String) = ArtifactRef(Samples.repositoryId, RepoPath(path))

    fun symbolRef(id: String) = NodeRef.OfSymbol(SymbolId(id))

    fun symbol(
        id: String,
        name: String = id,
        artifact: String? = "src/A.kt",
        origin: SymbolOrigin = SymbolOrigin.Source,
    ): Symbol = Samples.symbol.copy(
        id = SymbolId(id),
        name = name,
        origin = origin,
        declaration = artifact?.let { SourceLocation(artifactRef(it), TextRange(1, 1, 2, 1)) },
    )

    fun edge(
        from: String,
        resolution: Resolution,
        kind: RelationKind = RelationKind.CALLS,
        evidence: EvidenceClass = EvidenceClass.COMPILER,
    ): Relationship = Samples.relationship.copy(
        from = symbolRef(from),
        kind = kind,
        subKind = null,
        resolution = resolution,
        evidenceClass = evidence,
    )

    fun batch(
        owner: String,
        facts: List<Fact>,
        producer: ProducerRef = this.producer,
        fingerprint: String = "fp-$owner",
    ) = FactBatch(producer, OwnershipUnit(OwnershipKind.ARTIFACT, owner), Fingerprint(fingerprint), facts)

    fun symbols(owner: String, vararg symbols: Symbol) = symbolBatch(owner, symbols.asList())

    fun symbolBatch(owner: String, symbols: List<Symbol>) = batch(owner, symbols.map { Fact.OfSymbol(it) })

    fun edges(owner: String, vararg edges: Relationship) = edgeBatch(owner, edges.asList())

    fun edgeBatch(owner: String, edges: List<Relationship>) = batch(owner, edges.map { Fact.OfRelationship(it) })
}
