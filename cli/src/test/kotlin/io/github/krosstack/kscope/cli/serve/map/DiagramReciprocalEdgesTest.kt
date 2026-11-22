package io.github.krosstack.kscope.cli.serve.map

import kotlin.test.Test
import kotlin.test.assertTrue

class DiagramReciprocalEdgesTest {
    private fun resource(name: String): String {
        val path = "/io/github/krosstack/kscope/cli/serve/map/$name"
        return checkNotNull(DiagramReciprocalEdgesTest::class.java.getResource(path)) { name }.readText()
    }

    @Test
    fun `an edge and its reverse edge are in one group, and the edge that goes up in id order comes first`() {
        val routes = resource("diagram-routes.js")

        assertTrue(routes.contains("export function bends(edges)"))
        assertTrue(routes.contains("const direction = (edge) => (edge.from <= edge.to ? 0 : 1)"))
        assertTrue(routes.contains("const BEND = 16"))
    }

    @Test
    fun `the bend moves the control points, so the label on the middle of the curve moves with the curve`() {
        val routes = resource("diagram-routes.js")

        assertTrue(routes.contains("export function curve(from, to, bend = 0)"))
        assertTrue(routes.contains("y: start.y + bend"))
        assertTrue(routes.contains("y: end.y + bend"))
        assertTrue(routes.contains("(start.y + 3 * first.y + 3 * second.y + end.y) / 8"))
    }

    @Test
    fun `the drawing hands the bend of every edge to the route that carries its path, hit area and label`() {
        val routes = resource("diagram-routes.js")
        val drawing = resource("diagram-draw.js")

        assertTrue(routes.contains("const bend = offsets.get(edge)"))
        assertTrue(drawing.contains("line(edge, routes.get(edge))"))
        assertTrue(drawing.contains("const { d, middle } = route"))
    }
}
