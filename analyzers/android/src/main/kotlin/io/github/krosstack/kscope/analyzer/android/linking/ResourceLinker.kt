package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
import io.github.krosstack.kscope.analyzer.android.resources.ResourceKey
import io.github.krosstack.kscope.analyzer.android.resources.ResourceSymbols
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private const val NOT_FOUND = "android.resource.not-found"
private const val SCOPE_UNKNOWN = "android.resource.scope-unknown"
private const val MALFORMED_KEY = "android.resource.malformed-key"
private const val CANDIDATES = "android.resource.candidates"

/**
 * Answers the references of resources to resources. A reference is looked up in the module it is made in first, among
 * the source sets by the precedence of Android, then in the modules that module depends on, nearest first. It is
 * exact when one module provides the resource and probable when several do.
 */
internal class ResourceLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.resource-linker", "0.1.0"),
        consumes = AndroidArtifactTypes.resources,
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override val namespaces: Set<String> = setOf(ResourceSymbols.NAMESPACE)

    override fun link(graph: GraphReader, sink: FactSink) {
        val references = graph.symbolicReferences(ResourceSymbols.NAMESPACE)
        if (references.isEmpty()) return
        val lookup = Lookup(graph)
        val facts = references.map { Fact.OfRelationship(lookup.answer(it)) }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }
}

private class Lookup(graph: GraphReader) {
    private val context = LinkContext(graph)
    private val index = ResourceIndex(graph.symbols(SymbolQuery(category = SymbolCategory.RESOURCE, limit = ALL)))
    private val precedence = context.precedence

    fun answer(reference: SymbolicReference): Relationship {
        val key = keyOf(reference)
        val origin = reference.scope.sourceSet?.let(context::sourceSet)
        return when {
            key == null -> reference.answeredBy(Resolution.NotAnalysed(reference.key, ReasonCode(MALFORMED_KEY)))
            origin == null -> reference.answeredBy(Resolution.NotAnalysed(reference.key, ReasonCode(SCOPE_UNKNOWN)))
            else -> resolve(reference, key, origin)
        }
    }

    private fun resolve(reference: SymbolicReference, key: ResourceKey, origin: SourceSet): Relationship {
        val own = index.definitions(origin.module, key)
        if (own.isNotEmpty()) return resolved(reference, precedence.best(own, origin.id), Certainty.EXACT, 1)
        val providers = context.dependencies(origin.module).map { index.definitions(it, key) }
            .filter { it.isNotEmpty() }
        return when {
            providers.isEmpty() -> reference.answeredBy(Resolution.Unresolved(reference.key, ReasonCode(NOT_FOUND)))
            providers.size == 1 -> resolved(reference, precedence.best(providers.single(), null), Certainty.EXACT, 1)
            else -> resolved(reference, precedence.best(providers.first(), null), Certainty.PROBABLE, providers.size)
        }
    }

    private fun resolved(reference: SymbolicReference, entry: LinkCandidate, certainty: Certainty, candidates: Int) =
        reference.answeredBy(
            Resolution.Resolved(NodeRef.OfSymbol(entry.symbolId), certainty),
            attributes = if (candidates > 1) mapOf(CANDIDATES to candidates.toString()) else emptyMap(),
        )

    private fun keyOf(reference: SymbolicReference): ResourceKey? {
        val type = reference.key.substringBefore('/', "")
        val name = reference.key.substringAfter('/', "")
        return if (type.isEmpty() || name.isEmpty()) null else ResourceKey(type, name)
    }
}
