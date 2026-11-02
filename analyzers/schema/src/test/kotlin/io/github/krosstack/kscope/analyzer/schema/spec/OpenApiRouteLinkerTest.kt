package io.github.krosstack.kscope.analyzer.schema.spec

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenApiRouteLinkerTest {
    @Test
    fun `an operation is bound to the route with its method and path`() {
        val linked = link(
            mapOf(
                SPEC to specification(operation("/tasks", "get", "post")),
                ROUTES to routes("get(\"/tasks\") { }", "post(\"/tasks\") { }"),
            ),
        )

        assertEquals(mapOf("getOperation" to "GET /tasks", "postOperation" to "POST /tasks"), linked.outcomes())
    }

    @Test
    fun `the binding is a probable convention from the operation to the route`() {
        val linked = link(mapOf(SPEC to specification(operation("/tasks", "get")), ROUTES to TASKS_ROUTE))
        val relationship = linked.relationships.single()

        assertEquals(RelationKind.BINDS, relationship.kind)
        assertEquals("openapi-route", relationship.subKind)
        assertEquals(EvidenceClass.CONVENTION, relationship.evidenceClass)
        assertEquals(Certainty.PROBABLE, (relationship.resolution as Resolution.Resolved).certainty)
        assertEquals(OwnershipKind.WORKSPACE, linked.batches.single().owner.kind)
    }

    @Test
    fun `the site of the binding is the declaration of the operation`() {
        val linked = link(mapOf(SPEC to specification(operation("/tasks", "get")), ROUTES to TASKS_ROUTE))
        val site = requireNotNull(linked.relationships.single().site)

        assertEquals(SPEC, site.artifact.path.value)
        assertEquals(4, site.range.startLine)
    }

    @Test
    fun `path parameters are compared as placeholders, whatever their names`() {
        val linked = link(
            mapOf(
                SPEC to specification(
                    operation("/tasks/{taskId}", "get"),
                    operation("/users/{userId}/tasks/{taskId}", "put"),
                    operation("/files/{name}.json", "delete"),
                    operation("/plain/{id}", "post"),
                ),
                ROUTES to routes(
                    "get(\"/tasks/{id}\") { }",
                    "route(\"/users/{user}\") { put(\"/tasks/{task?}\") { } }",
                    "delete(\"/files/{file}.json\") { }",
                    "post(\"/plain/{rest...}\") { }",
                ),
            ),
        )

        assertEquals(
            mapOf(
                "getOperation" to "GET /tasks/{id}",
                "putOperation" to "PUT /users/{user}/tasks/{task?}",
                "deleteOperation" to "DELETE /files/{file}.json",
            ),
            linked.outcomes(),
        )
    }

    @Test
    fun `a method or a path that differs is no match`() {
        val linked = link(
            mapOf(
                SPEC to specification(
                    operation("/tasks", "post"),
                    operation("/tasks/{id}", "get"),
                    operation("/Tasks", "put"),
                ),
                ROUTES to routes("get(\"/tasks\") { }", "get(\"/tasks/{id}/notes\") { }", "put(\"/tasks\") { }"),
            ),
        )

        assertEquals(emptyMap(), linked.outcomes())
    }

    @Test
    fun `routes in nested blocks and in other modules are found`() {
        val linked = link(
            mapOf(
                SPEC to specification(operation("/api/v1/tasks", "get"), operation("/health", "head")),
                ROUTES to routes("route(\"/api\") { route(\"/v1\") { get(\"/tasks\") { } } }"),
                "other/src/main/kotlin/Health.kt" to routes("head(\"/health\") { }"),
            ),
        )

        assertEquals(
            mapOf("getOperation" to "GET /api/v1/tasks", "headOperation" to "HEAD /health"),
            linked.outcomes(),
        )
    }
}
