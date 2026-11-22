package io.github.krosstack.kscope.cli.serve.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramRoutingTest {
    private fun resource(name: String): String {
        val path = "/io/github/krosstack/kscope/cli/serve/map/$name"
        return checkNotNull(DiagramRoutingTest::class.java.getResource(path)) { name }.readText()
    }

    private val routes = resource("diagram-routes.js")

    @Test
    fun `edges are grouped by the unordered pair of their nodes, so relations in one direction share a group`() {
        val up = "`${'$'}{edge.from}\\n${'$'}{edge.to}`"
        val down = "`${'$'}{edge.to}\\n${'$'}{edge.from}`"

        assertTrue(routes.contains("edge.from < edge.to ? $up : $down"))
        assertTrue(routes.contains("groups.values()"))
    }

    @Test
    fun `every edge of a group of two or more gets its own slot, alternating sides at growing distances`() {
        assertTrue(routes.contains("index % 2 === 0 ? 1 : -1"))
        assertTrue(routes.contains("BEND * (Math.floor(index / 2) + 1)"))
        assertTrue(routes.contains("group.length === 1 ? 0 : slot(index)"))
    }

    @Test
    fun `the slot of an edge follows its direction, then its label and position, never the order of the input`() {
        assertTrue(routes.contains("direction(a) - direction(b)"))
        assertTrue(routes.contains("compare(a.label, b.label)"))
        assertTrue(routes.contains("compare(a.position ?? 0, b.position ?? 0)"))
        assertTrue(routes.contains("group.sort(inOrder)"))
        assertFalse(routes.contains("localeCompare"))
    }

    @Test
    fun `an edge from a node to itself is drawn as a loop off the side of its node`() {
        assertTrue(routes.contains("edge.from === edge.to ? loop(from, bend) : curve(from, to, bend)"))
        assertTrue(routes.contains("const side = bend < 0 ? -1 : 1"))
        assertTrue(routes.contains("side > 0 ? rect.x + rect.w : rect.x"))
        assertTrue(routes.contains("Math.abs(bend)"))
    }

    @Test
    fun `the drawing bounds hold the sampled path of every edge and the box of its label`() {
        assertTrue(routes.contains("for (let step = 0; step <= STEPS; step++)"))
        assertTrue(routes.contains("STROKE_PAD"))
        assertTrue(routes.contains("edgeText(edge)"))
        assertTrue(routes.contains("text.length * LABEL_CHAR_WIDTH"))
        assertTrue(routes.contains("Math.floor(box.minX)"))
        assertTrue(routes.contains("Math.ceil(box.maxX)"))
    }

    @Test
    fun `the picture takes its view box from those bounds, and each edge draws the route that was found for it`() {
        val drawing = resource("diagram-draw.js")

        assertTrue(drawing.contains("const { routes, box } = routeEdges(diagram, place)"))
        assertTrue(drawing.contains("viewBox: `${'$'}{box.x} ${'$'}{box.y} ${'$'}{box.width} ${'$'}{box.height}`"))
        assertTrue(drawing.contains("width: box.width, height: box.height"))
        assertTrue(drawing.contains("routes.get(edge)"))
    }

    @Test
    fun `the routing file is served and the layout no longer routes`() {
        assertTrue(resource("MANIFEST").lines().contains("diagram-routes.js"))
        assertFalse(resource("diagram-layout.js").contains("export function curve"))
        assertFalse(resource("diagram-layout.js").contains("export function bends"))
    }
}
