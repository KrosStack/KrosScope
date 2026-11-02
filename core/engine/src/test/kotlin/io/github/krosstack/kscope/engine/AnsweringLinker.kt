package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.graph.GraphReader

/** Answers each reference of its namespace: `found..` resolves, `missing..` does not, the rest is not analysed. */
internal open class AnsweringLinker(id: String, override val namespaces: Set<String> = setOf(FAKE_NAMESPACE)) :
    BoundaryLinker {
    override val descriptor = descriptorOf(id, kotlinSource, OwnershipKind.WORKSPACE)
    var linkCalls = 0
        private set

    override fun link(graph: GraphReader, sink: FactSink) {
        linkCalls++
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "ignored")
        val answers = namespaces.sorted().flatMap { graph.symbolicReferences(it) }.flatMap(::answersTo)
        val facts = answers.map { Fact.OfRelationship(it) }
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint("ignored"), facts))
    }

    protected open fun answersTo(reference: SymbolicReference): List<Relationship> =
        listOf(reference.answeredBy(resolutionOf(reference)))

    private fun resolutionOf(reference: SymbolicReference): Resolution = when {
        reference.key.startsWith("found") ->
            Resolution.Resolved(NodeRef.OfModule(ModuleId("m")), Certainty.EXACT)

        reference.key.startsWith("missing") -> Resolution.Unresolved(reference.key, ReasonCode("fake.not-found"))

        else -> Resolution.NotAnalysed(reference.key, ReasonCode("fake.not-analysed"))
    }
}
