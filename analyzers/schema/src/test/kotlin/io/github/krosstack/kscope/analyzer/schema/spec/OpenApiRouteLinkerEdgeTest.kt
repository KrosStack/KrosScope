package io.github.krosstack.kscope.analyzer.schema.spec

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiRouteLinkerEdgeTest {
    @Test
    fun `a relative servers path is tried as a prefix when the path alone matches nothing`() {
        val servers = "servers:\n  - url: /api/v1/\n"
        val linked = link(
            mapOf(
                SPEC to specification(operation("/tasks", "get"), servers = servers),
                ROUTES to routes("get(\"/api/v1/tasks\") { }", "get(\"/v9/tasks\") { }"),
            ),
        )

        assertEquals(mapOf("getOperation" to "GET /api/v1/tasks"), linked.outcomes())
    }

    @Test
    fun `the path of an absolute server is tried as a prefix as well`() {
        val servers = "servers:\n  - url: /api/v1/\n  - url: https://example.com/v9\n"
        val linked = link(
            mapOf(
                SPEC to specification(operation("/tasks", "get"), servers = servers),
                ROUTES to routes("get(\"/v9/tasks\") { }", "get(\"/v7/tasks\") { }"),
            ),
        )

        assertEquals(mapOf("getOperation" to "GET /v9/tasks"), linked.outcomes())
    }

    @Test
    fun `the servers prefix is not tried when a route has the path of the document`() {
        val servers = "servers:\n  - url: /api\n"
        val linked = link(
            mapOf(
                SPEC to specification(operation("/tasks", "get"), servers = servers),
                ROUTES to routes("get(\"/tasks\") { }", "get(\"/api/tasks\") { }"),
            ),
        )

        assertEquals(mapOf("getOperation" to "GET /tasks"), linked.outcomes())
    }

    @Test
    fun `several routes with the path of an operation are an unresolved ambiguity`() {
        val linked = link(
            mapOf(
                SPEC to specification(operation("/tasks", "get")),
                ROUTES to TASKS_ROUTE,
                "other/src/main/kotlin/Again.kt" to TASKS_ROUTE,
            ),
        )
        val resolution = linked.relationships.single().resolution as Resolution.Unresolved

        assertEquals(mapOf("getOperation" to AMBIGUOUS), linked.outcomes())
        assertEquals("GET /tasks", resolution.hint)
    }

    @Test
    fun `several routes that placeholders make equal are an ambiguity too`() {
        val linked = link(
            mapOf(
                SPEC to specification(operation("/tasks/{id}", "get")),
                ROUTES to routes("get(\"/tasks/{a}\") { }", "get(\"/tasks/{b}\") { }"),
            ),
        )

        assertEquals(mapOf("getOperation" to AMBIGUOUS), linked.outcomes())
    }

    @Test
    fun `an operation with no route makes no fact, and a workspace with no operation sends nothing`() {
        val none = link(mapOf(SPEC to specification(operation("/tasks", "get")), ROUTES to "class Plain\n"))
        val noOperations = link(mapOf(ROUTES to TASKS_ROUTE))

        assertEquals(emptyList(), none.relationships)
        assertTrue(none.batches.all { it.facts.isEmpty() })
        assertEquals(emptyList(), noOperations.batches)
    }

    @Test
    fun `the facts come in the order of the operations`() {
        val linked = link(
            mapOf(
                SPEC to specification(operation("/b", "get"), operation("/a", "get")),
                ROUTES to routes("get(\"/a\") { }", "get(\"/b\") { }"),
            ),
        )
        val sources = linked.relationships.map { (it.from as NodeRef.OfSymbol).id.value }

        assertEquals(2, sources.size)
        assertEquals(sources.sorted(), sources)
    }
}
