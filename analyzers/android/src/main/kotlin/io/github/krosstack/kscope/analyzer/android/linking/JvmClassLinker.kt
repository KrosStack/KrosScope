package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
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

private const val NOT_FOUND = "jvm.class.not-found"
private const val SCOPE_UNKNOWN = "jvm.class.scope-unknown"
private const val CANDIDATES = "jvm.class.candidates"
private const val TYPE_ALIAS = "kotlin.typealias"

/**
 * Answers the references from manifests, layouts and navigation graphs to classes by name. A class is looked up in the
 * module the reference is made in first, then in the modules that module depends on, nearest first. The name decides,
 * so the evidence is textual; the answer is exact when one class matches and probable when several declarations of
 * it exist, such as one per variant.
 */
internal class JvmClassLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.jvm-class-linker", "0.1.0"),
        consumes = setOf(AndroidArtifactTypes.manifest, AndroidArtifactTypes.layout, AndroidArtifactTypes.navigation),
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override val namespaces: Set<String> = setOf(JvmClasses.NAMESPACE)

    override fun link(graph: GraphReader, sink: FactSink) {
        val references = graph.symbolicReferences(JvmClasses.NAMESPACE)
        if (references.isEmpty()) return
        val lookup = ClassLookup(graph)
        val facts = references.map { Fact.OfRelationship(lookup.answer(it)) }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }
}

private class ClassLookup(private val graph: GraphReader) {
    private val context = LinkContext(graph)
    private val classesByName = HashMap<String, List<LinkCandidate>>()

    fun answer(reference: SymbolicReference): Relationship {
        val origin = reference.scope.sourceSet?.let(context::sourceSet)
        return if (origin == null) {
            reference.answeredBy(Resolution.NotAnalysed(reference.key, ReasonCode(SCOPE_UNKNOWN)))
        } else {
            resolve(reference, origin)
        }
    }

    private fun resolve(reference: SymbolicReference, origin: SourceSet): Relationship {
        val classes = classesNamed(reference.key)
        val visible = (listOf(origin.module) + context.dependencies(origin.module))
            .firstNotNullOfOrNull { module -> classes.filter { it.module == module }.takeIf { it.isNotEmpty() } }
        return when {
            visible == null -> reference.answeredBy(Resolution.Unresolved(reference.key, ReasonCode(NOT_FOUND)))
            visible.size == 1 -> resolved(reference, visible.single(), Certainty.EXACT, 1)
            else -> resolved(reference, best(visible, origin), Certainty.PROBABLE, visible.size)
        }
    }

    private fun best(visible: List<LinkCandidate>, origin: SourceSet): LinkCandidate =
        context.precedence.best(visible, origin.id.takeIf { visible.first().module == origin.module })

    private fun resolved(reference: SymbolicReference, target: LinkCandidate, certainty: Certainty, count: Int) =
        reference.answeredBy(
            Resolution.Resolved(NodeRef.OfSymbol(target.symbolId), certainty),
            attributes = if (count > 1) mapOf(CANDIDATES to count.toString()) else emptyMap(),
        )

    private fun classesNamed(qualifiedName: String): List<LinkCandidate> = classesByName.getOrPut(qualifiedName) {
        graph.symbols(SymbolQuery(qualifiedName = qualifiedName, limit = ALL))
            .filter { it.kind.category == SymbolCategory.TYPE && it.kind.id != TYPE_ALIAS }
            .mapNotNull(context::candidateOf)
            .sortedBy { it.symbolId.value }
    }
}
