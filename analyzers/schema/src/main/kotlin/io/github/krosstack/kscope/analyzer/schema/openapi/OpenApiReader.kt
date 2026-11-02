package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.document.DocEntry
import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocNode
import io.github.krosstack.kscope.analyzer.schema.document.DocSequence

private const val COMPONENT_SCHEMAS = "components/schemas"
private const val DEFINITIONS = "definitions"
private val absoluteUrl = Regex("""^[A-Za-z][A-Za-z0-9+.-]*://[^/?#]*(/[^?#]*)?""")

/**
 * Takes the operations, the schemas and the server paths out of the tree of an OpenAPI 3 or Swagger 2 document. A
 * server is kept by its path, whether the URL is relative or absolute; a server with no path is not kept.
 */
internal class OpenApiReader {
    /** Null when the tree is no mapping or declares neither `openapi` nor `swagger`. */
    fun read(root: DocNode?): OpenApiDocument? {
        val mapping = root as? DocMapping
        val version = mapping?.let { it.entry("openapi") ?: it.entry("swagger") }
        return if (mapping == null || version == null) null else documentOf(mapping, version)
    }

    private fun documentOf(root: DocMapping, version: DocEntry) = OpenApiDocument(
        version = version,
        title = root.mapping("info")?.text("title")?.takeIf { it.isNotBlank() },
        servers = serversOf(root),
        operations = OpenApiOperationReader(root).operations(),
        schemas = schemasOf(root),
    )

    private fun schemasOf(root: DocMapping): List<OpenApiSchema> {
        val components = root.mapping("components")?.mapping("schemas")?.let { COMPONENT_SCHEMAS to it }
        val definitions = root.mapping(DEFINITIONS)?.let { DEFINITIONS to it }
        return listOfNotNull(components, definitions).flatMap { (section, schemas) ->
            schemas.entries.map {
                OpenApiSchema(it.key, section, it.span, it.keySpan, OpenApiSchemaReader.propertiesOf(it.value))
            }
        }
    }

    private fun serversOf(root: DocMapping): List<String> {
        val urls = (root.entry("servers")?.value as? DocSequence)?.items.orEmpty()
            .mapNotNull { (it as? DocMapping)?.text("url") }
        return (urls + listOfNotNull(root.text("basePath"))).mapNotNull(::serverPathOf).distinct()
    }

    private fun serverPathOf(url: String): String? {
        val trimmed = url.trim()
        val path = when {
            trimmed.startsWith("//") -> null
            trimmed.startsWith("/") -> trimmed.substringBefore('?').substringBefore('#')
            else -> absoluteUrl.find(trimmed)?.groups?.get(1)?.value
        }
        return path?.trimEnd('/')?.takeIf { it.isNotEmpty() }
    }
}
