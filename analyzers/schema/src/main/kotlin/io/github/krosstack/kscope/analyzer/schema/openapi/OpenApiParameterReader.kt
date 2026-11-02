package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocNode
import io.github.krosstack.kscope.analyzer.schema.document.DocSequence

private const val PATH = "path"
private const val BODY = "body"
private val clientPlaces = setOf(PATH, "query", "header")
private const val COMPONENT_PARAMETERS = "#/components/parameters/"
private const val DEFINED_PARAMETERS = "#/parameters/"

/** The parameters of an operation that a client takes, and its body when a Swagger 2 document gives it as one. */
internal class OpenApiParameters(val parameters: List<OpenApiParameter>, val body: OpenApiBody?)

/**
 * Reads the parameters of an operation together with those of its path item, which an operation of the same name and
 * place replaces. A parameter given as a reference to the parameters of the document is read from there; a reference
 * to anything else, and a parameter in a cookie or a form, are left out.
 */
internal class OpenApiParameterReader(private val root: DocMapping) {
    fun read(item: DocMapping, operation: DocMapping): OpenApiParameters {
        val own = nodesOf(operation)
        val shared = nodesOf(item).filter { inherited -> own.none { isSame(it, inherited) } }
        val nodes = shared + own
        return OpenApiParameters(nodes.mapNotNull(::parameterOf), swaggerBody(nodes))
    }

    private fun nodesOf(owner: DocMapping): List<DocMapping> =
        (owner.entry("parameters")?.value as? DocSequence)?.items.orEmpty().mapNotNull(::resolved)

    private fun resolved(node: DocNode): DocMapping? {
        val mapping = node as? DocMapping
        val reference = mapping?.text(REFERENCE_KEY)
        return if (reference == null) mapping else targetOf(reference)
    }

    private fun targetOf(reference: String): DocMapping? = when {
        reference.startsWith(COMPONENT_PARAMETERS) ->
            root.mapping("components")?.mapping("parameters")?.mapping(reference.removePrefix(COMPONENT_PARAMETERS))

        reference.startsWith(DEFINED_PARAMETERS) ->
            root.mapping("parameters")?.mapping(reference.removePrefix(DEFINED_PARAMETERS))

        else -> null
    }

    private fun parameterOf(node: DocMapping): OpenApiParameter? {
        val name = node.text("name")
        val place = node.text("in")
        return if (name == null || place == null || place !in clientPlaces) {
            null
        } else {
            val isRequired = place == PATH || node.text("required") == "true"
            OpenApiParameter(name, place, isRequired, OpenApiTypeReader.typeOf(node.entry("schema")?.value ?: node))
        }
    }

    private fun swaggerBody(nodes: List<DocMapping>): OpenApiBody? {
        val node = nodes.firstOrNull { it.text("in") == BODY }
        val schema = node?.entry("schema")?.value
        return if (node == null || schema == null) {
            null
        } else {
            OpenApiBody(OpenApiTypeReader.typeOf(schema), node.text("required") == "true")
        }
    }

    private fun isSame(first: DocMapping, second: DocMapping): Boolean =
        first.text("name") == second.text("name") && first.text("in") == second.text("in")
}
