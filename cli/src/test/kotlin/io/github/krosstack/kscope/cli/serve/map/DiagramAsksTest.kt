package io.github.krosstack.kscope.cli.serve.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiagramAsksTest {
    private fun refusal(vararg values: Pair<String, String>): String =
        assertIs<DiagramAsk.Refused>(DiagramAsks.of(mapOf(*values))).message

    @Test
    fun `a question that names only a node gets one step and the default lines`() {
        val ask = DiagramAsks.of(mapOf("node" to "s:1"))

        assertEquals(DiagramAsk.Valid("s:1", 1, DiagramAsks.DEFAULT_LINKS), ask)
    }

    @Test
    fun `depth is a number of steps or all, and all asks for no limit`() {
        val steps = DiagramAsks.of(mapOf("node" to "s:1", "depth" to "3", "links" to "0"))
        val all = DiagramAsks.of(mapOf("node" to "s:1", "depth" to "all", "links" to "1000"))

        assertEquals(DiagramAsk.Valid("s:1", 3, 0), steps)
        assertEquals(DiagramAsk.Valid("s:1", null, DiagramAsks.MAX_LINKS), all)
    }

    @Test
    fun `a question without a node is refused`() {
        assertEquals("Name a symbol with node.", refusal())
        assertEquals("Name a symbol with node.", refusal("node" to "  "))
    }

    @Test
    fun `a depth outside one to fifty and not all is refused`() {
        listOf("0", "-1", "51", "two", "", "ALL").forEach { depth ->
            assertEquals(
                "depth is a whole number from 1 to 50, or all.",
                refusal("node" to "s:1", "depth" to depth),
                depth,
            )
        }
    }

    @Test
    fun `lines outside zero to a thousand are refused`() {
        listOf("-1", "1001", "many", "").forEach { links ->
            assertEquals(
                "links is a whole number from 0 to 1000.",
                refusal("node" to "s:1", "links" to links),
                links,
            )
        }
    }
}
