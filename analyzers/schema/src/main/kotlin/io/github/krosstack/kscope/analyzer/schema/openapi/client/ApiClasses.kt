package io.github.krosstack.kscope.analyzer.schema.openapi.client

import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiIdentities
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiOperation
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiType
import io.github.krosstack.kscope.analyzer.schema.stub.StubClass
import io.github.krosstack.kscope.analyzer.schema.stub.StubForm
import io.github.krosstack.kscope.analyzer.schema.stub.StubFunction
import io.github.krosstack.kscope.analyzer.schema.stub.StubParameter
import io.github.krosstack.kscope.analyzer.schema.stub.StubTruth
import io.github.krosstack.kscope.analyzer.schema.stub.StubTypes

private const val DEFAULT_API = "DefaultApi"
private const val UNIT = "kotlin.Unit"
private const val BODY = "body"

/**
 * The API classes of a client: one class for each tag, named after it and ending in `Api`, and `DefaultApi` for the
 * operations with no tag. An operation with several tags is in each of their classes. It is a function named by its
 * operation id, with its path, query and header parameters and its body, the required ones first, and it returns the
 * type of its response, or `Unit`. An operation with no operation id has no function. The function is generated from
 * its operation.
 */
internal class ApiClasses(
    private val operations: List<OpenApiOperation>,
    private val ids: OpenApiIdentities,
    private val types: ClientTypes,
) {
    fun classes(): List<StubClass> = operations.filter { it.operationId != null }
        .flatMap { operation -> classNamesOf(operation).map { it to operation } }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (name, members) -> StubClass(name = name, form = StubForm.CLASS, members = functionsOf(members)) }
        .filter { it.members.isNotEmpty() }

    private fun classNamesOf(operation: OpenApiOperation): List<String> =
        operation.tags.map { "${ClientNames.upperCamel(it)}Api" }.distinct().ifEmpty { listOf(DEFAULT_API) }

    private fun functionsOf(members: List<OpenApiOperation>): List<StubFunction> =
        members.mapNotNull { operation -> operation.operationId?.let { functionOf(operation, it) } }
            .distinctBy { function -> function.name to function.parameters.map { StubTypes.simpleName(it.type) } }

    private fun functionOf(operation: OpenApiOperation, operationId: String) = StubFunction(
        name = ClientNames.lowerCamel(operationId),
        parameters = parametersOf(operation),
        returnType = operation.response?.let(types::of) ?: UNIT,
        truth = StubTruth(ids.operationId(operation), direct = true),
        typeSource = operation.response?.let(types::sourceOf),
    )

    private fun parametersOf(operation: OpenApiOperation): List<StubParameter> {
        val given = operation.parameters.map { types.parameter(ClientNames.lowerCamel(it.name), it.type, it.required) }
        val body = operation.body?.let { types.parameter(bodyNameOf(it.type), it.type, it.required) }
        return (given + listOfNotNull(body)).distinctBy { it.name }.sortedBy { it.default != null }
    }

    private fun bodyNameOf(type: OpenApiType): String =
        if (type is OpenApiType.Reference) ClientNames.lowerCamel(type.schema) else BODY
}
