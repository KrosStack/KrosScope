package io.github.krosstack.kscope.cli.serve.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagramNarrowScreenTest {
    private fun resource(name: String): String {
        val path = "/io/github/krosstack/kscope/cli/serve/map/$name"
        return checkNotNull(DiagramNarrowScreenTest::class.java.getResource(path)) { name }.readText()
    }

    private val stacked = Regex("""@media \(max-width: (\d+)px\)""").find(resource("style.css"))
        ?.groupValues?.get(1)?.toInt()

    @Test
    fun `the wide panel sets its two columns only above the width where the page stacks map and panel`() {
        val css = resource("diagram.css")

        assertTrue(css.contains("@media (min-width: ${stacked?.plus(1)}px) {\n  .stage.wide {"))
        assertFalse(Regex("""(?m)^\.stage\.wide""").containsMatchIn(css))
    }

    @Test
    fun `the button that widens the panel is hidden where the panel already has the full width`() {
        val css = resource("diagram.css")

        assertTrue(css.contains("@media (max-width: ${stacked}px) {\n  .diagram-tools .widen { display: none; }"))
        assertTrue(resource("diagram.js").contains("class: \"plain widen\""))
    }
}
