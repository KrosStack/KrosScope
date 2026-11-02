package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.buildScript
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult

/** The structural model plus a symbol and a reference to it, which travel beside the model. */
internal class AdditionalFactsProvider(
    private val delegate: StructuralBuildModelProvider = StructuralBuildModelProvider(),
) : BuildModelProvider {
    override val producer = ProducerRef("test.additional-facts", "1")
    override val requiresTrust = false
    var calls = 0
        private set

    override fun provide(request: BuildModelRequest): BuildModelResult {
        calls++
        val provided = delegate.provide(request) as? BuildModelResult.Provided ?: return BuildModelResult.NotApplicable
        val root = provided.model.buildRoots.first()
        val symbol = catalogSymbol(root.id)
        val script = request.artifacts.first { it.type == buildScript }
        val reference = Relationship(
            from = NodeRef.OfArtifact(script.ref),
            kind = RelationKind.REFERENCES,
            subKind = "catalog.library",
            resolution = Resolution.Resolved(NodeRef.OfSymbol(symbol.id), Certainty.EXACT),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = null,
            scope = Scope(),
        )
        return provided.copy(facts = listOf(Fact.OfSymbol(symbol), Fact.OfRelationship(reference)))
    }

    private fun catalogSymbol(unit: BuildRootId): Symbol {
        val descriptor = SymbolDescriptor().namespace("gradle").namespace("catalog").namespace("libs")
            .namespace("libraries").term("junit")
        val identity = SymbolIdentity(SymbolEcosystem.GRADLE, unit.value, ".", descriptor)
        return Symbol(
            id = identity.toSymbolId(),
            kind = SymbolKind("gradle.catalog.library", SymbolCategory.BUILD),
            name = "junit",
            qualifiedName = "libs.junit",
            signature = null,
            container = null,
            visibility = null,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = null,
            nameLocation = null,
            documentation = null,
            scope = Scope(),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
        )
    }
}
