package io.github.krosstack.kscope.cli.serve.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramPageTest {
    private fun resource(name: String): String =
        checkNotNull(DiagramPageTest::class.java.getResource("/io/github/krosstack/kscope/cli/serve/map/$name")) {
            name
        }.readText()

    private val manifest = resource("MANIFEST").lines().filter(String::isNotBlank)
    private val diagramScripts =
        listOf(
            "diagram.js",
            "diagram-draw.js",
            "diagram-layout.js",
            "diagram-names.js",
            "diagram-routes.js",
            "diagram-words.js",
        )

    @Test
    fun `the files of the diagram are listed in the manifest`() {
        assertEquals(emptySet(), (diagramScripts + "diagram.css").toSet() - manifest.toSet())
    }

    @Test
    fun `the panel draws the diagram and the page loads its style`() {
        assertTrue(resource("panel.js").contains("from \"./diagram.js\""))
        assertTrue(resource("index.html").contains("href=\"app/diagram.css\""))
        assertTrue(resource("api.js").contains("api/diagram?node="))
    }

    @Test
    fun `the diagram is built from elements, never from text that is parsed as markup or as style`() {
        diagramScripts.forEach { name ->
            val script = resource(name)

            assertFalse(script.contains("innerHTML"), name)
            assertFalse(script.contains("insertAdjacentHTML"), name)
            assertFalse(script.contains("eval("), name)
            assertFalse(Regex("""["']style["']""").containsMatchIn(script), name)
        }
    }

    @Test
    fun `the diagram answers to the keyboard like it does to the pointer`() {
        val drawing = resource("diagram-draw.js")

        assertTrue(drawing.contains("tabindex"))
        assertTrue(drawing.contains("\"keydown\""))
    }

    @Test
    fun `a node is selected by the symbol id that it carries, and a node without one is not clickable`() {
        val drawing = resource("diagram-draw.js")

        assertTrue(drawing.contains("node.symbolId"))
        assertTrue(drawing.contains("files?.[node.symbolId]"))
        assertFalse(drawing.contains("symbols"))
    }

    @Test
    fun `a node shows its short name, found from the type boundaries of its symbol id`() {
        val names = resource("diagram-names.js")
        val drawing = resource("diagram-draw.js")

        assertTrue(names.contains("node.symbolId"))
        assertTrue(names.contains("""mark === "#""""))
        assertTrue(names.contains("""names.join(".")"""))
        assertTrue(drawing.contains("shownName(node)"))
        assertFalse(drawing.contains("shown(node.label)"))
    }

    @Test
    fun `a node without a readable symbol id shows its label`() {
        assertTrue(resource("diagram-names.js").contains(": node.label"))
    }

    @Test
    fun `a short name that is too long is cut at the start, and the layout measures the same text`() {
        val names = resource("diagram-names.js")

        assertTrue(names.contains("text.slice(text.length - (MAX_SHOWN - 1))"))
        assertTrue(names.contains("`…" + "${'$'}{text.slice"))
        assertTrue(resource("diagram-layout.js").contains("shownName(node).length"))
    }

    @Test
    fun `the tooltip and the accessible name hold the full name built from the symbol id`() {
        val drawing = resource("diagram-draw.js")
        val names = resource("diagram-names.js")

        assertTrue(drawing.contains("const full = qualifiedName(node)"))
        assertTrue(drawing.contains("words(`${'$'}{full}\\n"))
        assertTrue(drawing.contains("Show the diagram of ${'$'}{full}"))
        assertTrue(drawing.contains("""attributes["aria-label"] = full"""))
        assertFalse(drawing.contains("${'$'}{node.label}"))
        assertTrue(names.contains("[...parts.packages, ...parts.names].join(\".\")"))
        assertTrue(names.contains("""mark === "/""""))
    }

    @Test
    fun `a node that the keyboard selected hands the focus to the selected node of the new diagram`() {
        val drawing = resource("diagram-draw.js")
        val view = resource("diagram.js")

        assertTrue(drawing.contains("onSelect(tie, true)"))
        assertTrue(drawing.contains("onSelect(tie, false)"))
        assertTrue(drawing.contains("tabindex: -1"))
        assertTrue(view.contains("""picture.querySelector('[aria-current="true"]')?.focus()"""))
        assertTrue(view.contains("if (focus)"))
    }
}
