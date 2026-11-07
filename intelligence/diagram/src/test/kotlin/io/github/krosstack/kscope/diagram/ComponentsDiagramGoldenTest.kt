package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.diagram.emit.DiagramJson
import io.github.krosstack.kscope.diagram.emit.DotEmitter
import io.github.krosstack.kscope.diagram.emit.MermaidEmitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ComponentsDiagramGoldenTest {
    @Test
    fun `component diagrams match all three goldens`() {
        val diagram = (
            DiagramBuilder(DiagramGraph()).build(
                DiagramRequest(DiagramKind.COMPONENTS),
            ) as DiagramResult.Built
            ).diagram
        val rendered = mapOf(
            "mmd" to MermaidEmitter.render(diagram),
            "dot" to DotEmitter.render(diagram),
            "json" to DiagramJson.render(diagram),
        )
        rendered.forEach { (extension, actual) ->
            val expected = requireNotNull(javaClass.getResource("/components.$extension")).readText()
            assertEquals(expected, actual, extension)
        }
    }

    @Test
    fun `component projection includes every boundary sub kind and excludes internal module dependencies`() {
        val diagram = (
            DiagramBuilder(DiagramGraph()).build(
                DiagramRequest(DiagramKind.COMPONENTS),
            ) as DiagramResult.Built
            ).diagram
        val kinds = diagram.edges.map { it.label.substringBefore(';') }.toSet()
        assertEquals(
            setOf(
                "depends_on:maven-coordinates",
                "depends_on:jvm-library",
                "depends_on:native-library",
                "depends_on:web-bundle",
                "depends_on:generator-input",
                "binds:openapi-route",
                "binds:native-library",
                "depends_on",
            ),
            kinds,
        )
        assertTrue(diagram.edges.none { it.from == it.to })
    }
}
