package io.github.krosstack.kscope.analyzer.schema.openapi.client

import io.github.krosstack.kscope.analyzer.schema.IdentityFactory
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiIdentities
import io.github.krosstack.kscope.analyzer.schema.openapi.documentOf
import io.github.krosstack.kscope.analyzer.schema.stub.StubClass
import io.github.krosstack.kscope.analyzer.schema.stub.StubFunction
import io.github.krosstack.kscope.analyzer.schema.stub.StubParameter
import io.github.krosstack.kscope.model.ModuleId

internal val ids = OpenApiIdentities(IdentityFactory(ModuleId("spec//:"), "main"), "openapi/api.yaml")
internal val packages = ClientPackages("sample.api.apis", "sample.api.models")

internal fun filesOf(yaml: String): ClientFiles = OpenApiClientPlan(documentOf(yaml), ids, packages).files()

internal fun ClientFiles.apiClasses(): List<StubClass> = apis?.classes.orEmpty()

internal fun ClientFiles.modelClasses(): List<StubClass> = models?.classes.orEmpty()

internal fun StubClass.functions(): List<StubFunction> = members.filterIsInstance<StubFunction>()

internal fun StubParameter.written(): String = "$name: $type" + default?.let { " = $it" }.orEmpty()

internal fun StubFunction.written(): String = "$name(${parameters.joinToString { it.written() }}): $returnType"

internal fun operations(vararg operations: String): String =
    "openapi: 3.0.3\npaths:\n  /x:\n" + operations.joinToString("") { it.prependIndent("    ") + "\n" }

internal fun schemas(vararg schemas: String): String =
    "openapi: 3.0.3\ncomponents:\n  schemas:\n" + schemas.joinToString("") { it.prependIndent("    ") + "\n" }

internal fun jsonResponse(schema: String, code: String = "200") =
    "responses:\n  \"$code\":\n    content:\n      application/json:\n        schema: $schema".prependIndent("  ")

internal val TASKS_SPEC = """
    openapi: 3.0.3
    paths:
      /tasks:
        get:
          operationId: listTasks
          tags:
            - Tasks
          responses:
            "200":
              content:
                application/json:
                  schema:
                    type: array
                    items:
                      ${'$'}ref: "#/components/schemas/Task"
    components:
      schemas:
        Task:
          type: object
          required:
            - id
            - title
          properties:
            id:
              type: string
            title:
              type: string
""".trimIndent()
