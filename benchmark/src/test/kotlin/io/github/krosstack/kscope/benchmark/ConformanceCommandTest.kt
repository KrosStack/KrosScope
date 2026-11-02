package io.github.krosstack.kscope.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConformanceCommandTest {
    private fun run(args: List<String>): Pair<Int, List<String>> {
        val printed = mutableListOf<String>()
        return runCommand(args, printed::add) to printed
    }

    @Test
    fun `arguments that do not name a command with a mode and an output directory print the usage`() {
        listOf(
            emptyList(),
            listOf("conformance"),
            listOf("conformance", "--mode", "default"),
            listOf("conformance", "--mode", "sideways", "--out", "x"),
            listOf("conformance", "--level", "deeper", "--out", "x"),
            listOf("other", "--mode", "default", "--out", "x"),
        ).forEach { args ->
            val (code, printed) = run(args)
            assertEquals(USAGE_ERROR, code, args.joinToString(" "))
            assertTrue(printed.single().startsWith("usage: conformance"))
        }
    }

    private companion object {
        const val USAGE_ERROR = 2
    }
}
