package io.github.krosstack.kscope.analyzer.schema.spec

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteShapeTest {
    @Test
    fun `a parameter is a placeholder whatever its name, its optional mark or its place in a segment`() {
        assertEquals("/tasks/{}", routeShape("/tasks/{id}"))
        assertEquals("/tasks/{}", routeShape("/tasks/{taskId?}"))
        assertEquals("/files/{}.json", routeShape("/files/{name}.json"))
        assertEquals("/{}/notes/{}", routeShape("/{a}/notes/{b}"))
    }

    @Test
    fun `a tailcard is not a placeholder`() {
        assertEquals("/files/<tail>", routeShape("/files/{path...}"))
        assertEquals("/files/{}", routeShape("/files/{path}"))
    }

    @Test
    fun `slashes are single, the first is there and the last is gone but for the root`() {
        assertEquals("/", routeShape(""))
        assertEquals("/", routeShape("/"))
        assertEquals("/tasks", routeShape("tasks/"))
        assertEquals("/a/b", routeShape("//a///b//"))
    }

    @Test
    fun `case and literal segments are kept`() {
        assertEquals("/Tasks/v1.0", routeShape("/Tasks/v1.0"))
    }
}
