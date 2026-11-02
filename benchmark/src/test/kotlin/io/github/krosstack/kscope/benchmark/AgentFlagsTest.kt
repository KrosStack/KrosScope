package io.github.krosstack.kscope.benchmark

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentFlagsTest {
    private val workingDirectory: Path = Path.of("").toAbsolutePath()

    private fun flags(vararg args: String): AgentFlags =
        checkNotNull(AgentFlags.parse(args.toList(), setOf("--tasks"), emptySet()))

    @Test
    fun `a relative path flag is made absolute against the working directory and normalised`() {
        val flags = flags("--tasks", "tasks/./nested/..")

        assertEquals(workingDirectory.resolve("tasks"), flags.path("--tasks", "fallback"))
    }

    @Test
    fun `the fallback of an absent flag is made absolute in the same way`() {
        assertEquals(workingDirectory.resolve("fallback"), flags().path("--tasks", "other/../fallback"))
    }

    @Test
    fun `an absolute path flag is kept`() {
        val absolute = workingDirectory.resolve("elsewhere")

        assertEquals(absolute, flags("--tasks", absolute.toString()).path("--tasks", "fallback"))
    }
}
