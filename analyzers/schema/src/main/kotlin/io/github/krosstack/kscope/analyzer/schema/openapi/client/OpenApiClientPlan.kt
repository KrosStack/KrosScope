package io.github.krosstack.kscope.analyzer.schema.openapi.client

import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiDocument
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiIdentities
import io.github.krosstack.kscope.analyzer.schema.stub.StubFile

/** The two files of a client: the one with its API classes and the one with its models, each null when it has none. */
internal class ClientFiles(val apis: StubFile?, val models: StubFile?)

/**
 * The Kotlin client that the OpenAPI Generator makes of a document with its `kotlin` generator, by its naming rules:
 * API classes in the API package and data classes in the model package. A schema has a class when it is a plain
 * object with properties; enumerations, compositions, primitives, arrays and objects with no properties have none,
 * and a reference to them is typed `Any`. The ids of the sources of truth come from [ids], which names the document.
 */
internal class OpenApiClientPlan(
    private val document: OpenApiDocument,
    private val ids: OpenApiIdentities,
    private val packages: ClientPackages,
) {
    fun files(): ClientFiles {
        val schemas = document.schemas.filter { it.properties.isNotEmpty() }
            .distinctBy { ClientNames.upperCamel(it.name) }
        val types = ClientTypes(schemas.associate { it.name to ids.schemaId(it) }, packages.model)
        val apis = ApiClasses(document.operations, ids, types).classes()
        val models = ModelClasses(schemas, ids, types).classes()
        return ClientFiles(
            apis = StubFile(packages.api, apis).takeIf { apis.isNotEmpty() },
            models = StubFile(packages.model, models).takeIf { models.isNotEmpty() },
        )
    }
}
