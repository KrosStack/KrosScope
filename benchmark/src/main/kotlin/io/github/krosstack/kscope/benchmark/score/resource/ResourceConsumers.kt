package io.github.krosstack.kscope.benchmark.score.resource

import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.evidenceOf
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin

/** The kinds of consumer that the class `resource_to_consumer` tells apart, so that a report can show each one. */
internal object ConsumerParts {
    const val CODE = "Kotlin/Java"
    const val CODE_LAYOUT = "Kotlin layout (view binding)"
    const val XML = "XML"
    const val MANIFEST = "Manifest"

    val all = listOf(CODE, CODE_LAYOUT, XML, MANIFEST)
}

/**
 * A file and line that refers to [resource]. [evidence] is the evidence class of the hop that gold describes;
 * [generator] names the generator whose surface a code consumer reaches the resource through, null for XML.
 */
internal class ConsumerEdge(
    val resource: Symbol,
    val path: String,
    val line: Int,
    val evidence: String,
    val generator: String? = null,
)

internal const val R_GENERATOR = "android.r"
internal const val VIEW_BINDING_GENERATOR = "android.viewbinding"
private const val RESOURCE_KIND_PREFIX = "android.resource."
private const val MANIFEST_KIND_PREFIX = "android.manifest."
private const val LAYOUT_KIND = "android.resource.layout"
private const val MANIFEST_TYPE = "android.manifest"
private const val DEFINITION = "definition"
private val codeTypes = setOf("kotlin.source", "java.source")

internal fun consumerPartOf(resourceKind: String, consumerType: String?): String = when {
    consumerType in codeTypes -> if (resourceKind == LAYOUT_KIND) ConsumerParts.CODE_LAYOUT else ConsumerParts.CODE
    consumerType == MANIFEST_TYPE -> ConsumerParts.MANIFEST
    else -> ConsumerParts.XML
}

/**
 * The places in the graph that refer to a resource. An XML consumer is a resolved reference from a resource or from a
 * component of the manifest to a resource. A Kotlin or Java consumer is the two-hop path of a generated accessor: a
 * reference from the code to the generated `R` field or to the view binding class of a layout, and the `GENERATES`
 * relationship from the resource to that surface.
 */
internal class ResourceConsumers(private val index: GraphIndex) {
    private val resourcesByTarget: Map<SymbolId, List<Relationship>> = index.relationships
        .filter { it.kind == RelationKind.GENERATES }
        .mapNotNull { generates -> targetSymbolId(generates)?.let { it to generates } }
        .groupBy({ it.first }, { it.second })

    fun edges(): List<ConsumerEdge> = distinctPlaces(index.relationships.flatMap(::edgesOf))

    /** The places that name the view binding class of a layout. */
    fun bindingEdges(): List<ConsumerEdge> =
        distinctPlaces(index.relationships.flatMap(::edgesOf).filter { it.generator == VIEW_BINDING_GENERATOR })

    private fun distinctPlaces(edges: List<ConsumerEdge>) =
        edges.distinctBy { listOf(it.resource.id, it.path, it.line) }

    fun typeOf(path: String): String? = index.artifactsByPath[path]?.type?.id

    private fun edgesOf(relationship: Relationship): List<ConsumerEdge> {
        val site = relationship.site?.takeIf { relationship.kind != RelationKind.GENERATES }
        val target = resolvedSymbol(relationship)
        val path = site?.artifact?.path?.value
        return when {
            site == null || target == null || path == null -> emptyList()
            isResourceFile(path) -> xmlEdge(relationship, target, path, site.range.startLine)
            generatorOf(target) != null -> generatedEdges(target, path, site.range.startLine)
            else -> emptyList()
        }
    }

    private fun xmlEdge(relationship: Relationship, target: Symbol, path: String, line: Int): List<ConsumerEdge> {
        val from = (relationship.from as? NodeRef.OfSymbol)?.id?.let { index.symbolsById[it] }
        val isReference = relationship.kind == RelationKind.REFERENCES && relationship.subKind != DEFINITION
        val fromKind = from?.kind?.id.orEmpty()
        val isFromResourceFile = fromKind.startsWith(RESOURCE_KIND_PREFIX) || fromKind.startsWith(MANIFEST_KIND_PREFIX)
        return if (isReference && isFromResourceFile && target.kind.id.startsWith(RESOURCE_KIND_PREFIX)) {
            listOf(ConsumerEdge(target, path, line, evidenceOf(relationship)))
        } else {
            emptyList()
        }
    }

    private fun generatedEdges(surface: Symbol, path: String, line: Int): List<ConsumerEdge> =
        resourcesByTarget[surface.id].orEmpty().mapNotNull { generates ->
            val resource = (generates.from as? NodeRef.OfSymbol)?.id?.let { index.symbolsById[it] }
            resource?.let { ConsumerEdge(it, path, line, evidenceOf(generates), generatorOf(surface)) }
        }

    private fun isResourceFile(path: String): Boolean {
        val type = index.artifactsByPath[path]?.type?.id.orEmpty()
        return type.startsWith(RESOURCE_KIND_PREFIX) || type == MANIFEST_TYPE
    }

    // Code reaches a resource through the field of `R`, or through the class that view binding makes for a layout.
    private fun generatorOf(symbol: Symbol): String? {
        val generator = (symbol.origin as? SymbolOrigin.Generated)?.generator
        val isField = generator == R_GENERATOR && symbol.kind.id == "java.field"
        val isBindingClass = generator == VIEW_BINDING_GENERATOR && symbol.kind.id == "java.class"
        return generator.takeIf { isField || isBindingClass }
    }

    private fun resolvedSymbol(relationship: Relationship): Symbol? =
        (index.resolvedTarget(relationship) as? NodeRef.OfSymbol)?.let { index.symbolsById[it.id] }

    private fun targetSymbolId(relationship: Relationship): SymbolId? =
        ((relationship.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id
}
