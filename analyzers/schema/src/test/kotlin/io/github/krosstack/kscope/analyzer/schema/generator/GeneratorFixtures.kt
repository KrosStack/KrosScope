package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.relationships
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol

internal const val TASKS_SPEC = """openapi: 3.0.3
info:
  title: TaskTide
  version: 1.0.0
paths:
  /tasks:
    get:
      operationId: listTasks
      tags:
        - Tasks
      responses:
        "200":
          description: The tasks, newest first.
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
"""

internal const val SYNC_PROTO = """syntax = "proto3";

package sample.tasktide.sync.v1;

message PushRequest {
  string device_id = 1;
  repeated string task_ids = 2;
}

message PushReply {
  int32 accepted = 1;
}
"""

internal const val CLIENT_SCRIPT = """plugins {
    kotlin("jvm")
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("${'$'}rootDir/../spec/openapi/tasktide.yaml")
    packageName.set("sample.tasktide.api")
}

wire {
    sourcePath {
        srcDir("${'$'}rootDir/../spec/proto")
    }
    kotlin {}
}
"""

internal const val SERVER_SCRIPT = """plugins {
    kotlin("jvm")
}

wire {
    sourcePath {
        srcDir("${'$'}rootDir/../spec/proto")
    }
    kotlin {}
}
"""

internal val wireKotlin = mapOf("wire.kotlin" to "true")

internal val defaultSpecFiles = mapOf(
    "openapi/tasktide.yaml" to TASKS_SPEC,
    "proto/sample/tasktide/sync/v1/sync.proto" to SYNC_PROTO,
)

/**
 * A workspace of an application and the repository of its specs, as the TaskTide fixture has them: `app` with the
 * modules `:client` and `:server` beside `spec`, whose one module holds all its files.
 */
internal fun tasktide(
    client: String = CLIENT_SCRIPT,
    server: String = SERVER_SCRIPT,
    clientModule: ModuleSpec = ModuleSpec(":client", attributes = wireKotlin),
    serverModule: ModuleSpec = ModuleSpec(":server", attributes = wireKotlin),
    appFiles: Map<String, String> = emptyMap(),
) = GeneratorWorkspace(
    listOf(
        RepositorySpec(
            "app",
            mapOf("build.gradle.kts" to "", "client/build.gradle.kts" to client, "server/build.gradle.kts" to server) +
                appFiles,
            listOf(ModuleSpec(":"), clientModule, serverModule),
        ),
        RepositorySpec("spec", defaultSpecFiles, listOf(ModuleSpec(":", roots = listOf("")))),
    ),
)

internal fun Synthesis.inputs(kind: String): List<Symbol> = facts.symbols().filter { it.kind.id == kind }

internal fun Synthesis.diagnostics(): List<Diagnostic> = facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value }

internal fun Synthesis.dependencies(): List<Relationship> =
    facts.relationships().filter { it.kind == RelationKind.DEPENDS_ON }

internal fun Synthesis.generates(): List<Relationship> =
    facts.relationships().filter { it.kind == RelationKind.GENERATES }

internal fun Relationship.target(): NodeRef = (resolution as Resolution.Resolved).target

internal fun lineOf(script: String, marker: String): Int = script.lines().indexOfFirst { marker in it } + 1
