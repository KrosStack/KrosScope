package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.retrieval.GraphScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphUseCasesTest {
    @Test
    fun `graph and related use cases refuse a workspace that was never scanned and create nothing`() {
        val workspace = Files.createTempDirectory("kscope-graph-api")
        try {
            KrosScope.open(workspace).use { scope ->
                assertFailsWith<NotIndexedException> { scope.graphView() }
                assertFailsWith<NotIndexedException> { scope.related("Absent") }
            }
            assertFalse(Files.exists(workspace.resolve(".kscope")))
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    @Test
    fun `graph and related use cases return serializable results for a node that is not in the index`() {
        val workspace = Files.createTempDirectory("kscope-graph-api")
        try {
            Files.writeString(workspace.resolve("settings.gradle.kts"), "include(\":lib\")\n")
            Files.createDirectories(workspace.resolve("lib/src/main/kotlin"))
            Files.writeString(workspace.resolve("lib/build.gradle.kts"), "")
            Files.writeString(workspace.resolve("lib/src/main/kotlin/Lib.kt"), "class Lib\n")
            KrosScope.open(workspace).use { scope ->
                scope.scan()
                val graph = scope.graphView()
                val related = scope.related("Absent")

                assertEquals(GraphScope.MODULES, graph.scope)
                assertFalse(graph.truncated)
                assertTrue(related.roots.isEmpty())
                assertEquals(
                    "modules",
                    Json.parseToJsonElement(ServiceJson.encode(graph))
                        .jsonObject.getValue("scope").jsonPrimitive.content,
                )
                assertEquals(
                    "Absent",
                    Json.parseToJsonElement(ServiceJson.encode(related))
                        .jsonObject.getValue("requested").jsonPrimitive.content,
                )
            }
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }
}
