package io.github.krosstack.kscope.cli.service.entry

import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProcessCommandRunnerTest {
    private val java: String = Path.of(System.getProperty("java.home"), "bin", "java").toString()

    private fun classPathOf(vararg types: Class<*>): String =
        types.joinToString(File.pathSeparator) { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }

    @Test
    fun `a command that succeeds gives its exit code and its output`() {
        val outcome = ProcessCommandRunner().run(listOf(java, "-version"))

        val finished = assertIs<CommandOutcome.Finished>(outcome)
        assertTrue(finished.succeeded)
        assertTrue(finished.output.contains("version"), finished.output)
    }

    @Test
    fun `a command that fails gives its exit code and its output`() {
        val outcome = ProcessCommandRunner().run(listOf(java, "--no-such-option"))

        val finished = assertIs<CommandOutcome.Finished>(outcome)
        assertFalse(finished.succeeded)
        assertNotEquals(0, finished.exitCode)
        assertTrue(finished.output.contains("no-such-option"), finished.output)
    }

    @Test
    fun `a program that does not exist is reported as unfinished`() {
        val outcome = ProcessCommandRunner().run(listOf("kscope-no-such-program-anywhere"))

        assertIs<CommandOutcome.Unfinished>(outcome)
    }

    @Test
    fun `a command that outlives the timeout is ended and reported as unfinished`() {
        val classPath = classPathOf(BlockingMain::class.java, Unit::class.java)
        val command = listOf(java, "-cp", classPath, BlockingMain::class.java.name)

        val outcome = ProcessCommandRunner(timeoutMillis = TIMEOUT_MILLIS).run(command)

        val unfinished = assertIs<CommandOutcome.Unfinished>(outcome)
        assertTrue(unfinished.reason.startsWith("timed out"), unfinished.reason)
        assertFalse(unfinished.succeeded)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 1_500L
    }
}
