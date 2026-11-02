package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.document.DocEntry
import io.github.krosstack.kscope.analyzer.schema.document.DocMapping
import io.github.krosstack.kscope.analyzer.schema.document.DocScalar
import io.github.krosstack.kscope.analyzer.schema.document.DocSequence

private val methods = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

/**
 * Takes the operations out of the `paths` of a document, in the order of the file. A path item that is only a
 * reference has no operations here, and extension keys (`x-`) are not paths.
 */
internal class OpenApiOperationReader(root: DocMapping) {
    private val paths = root.mapping("paths")
    private val parameters = OpenApiParameterReader(root)

    fun operations(): List<OpenApiOperation> =
        paths?.entries.orEmpty().filterNot { it.key.startsWith("x-") }.flatMap(::operationsOf)

    private fun operationsOf(path: DocEntry): List<OpenApiOperation> {
        val item = path.value as? DocMapping ?: return emptyList()
        return item.entries.filter { it.key in methods }.mapNotNull { method ->
            (method.value as? DocMapping)?.let { operationOf(path.key, method, item, it) }
        }
    }

    private fun operationOf(path: String, method: DocEntry, item: DocMapping, body: DocMapping): OpenApiOperation {
        val identifier = body.entry("operationId")
        val named = (identifier?.value as? DocScalar)?.takeIf { it.text.isNotBlank() }
        val given = parameters.read(item, body)
        return OpenApiOperation(
            method = method.key.uppercase(),
            path = path,
            operationId = named?.text,
            summary = body.text("summary"),
            declaration = method.span,
            nameSpan = named?.span ?: method.keySpan,
            tags = tagsOf(body),
            parameters = given.parameters,
            body = OpenApiPayloads.requestBody(body) ?: given.body,
            response = OpenApiPayloads.response(body),
        )
    }

    private fun tagsOf(operation: DocMapping): List<String> =
        (operation.entry("tags")?.value as? DocSequence)?.items.orEmpty().mapNotNull { (it as? DocScalar)?.text }
}
