package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessRunnerTest {
    private companion object {
        const val CHILD_EXIT_SECONDS = 10L
    }

    private val directory: Path = Files.createTempDirectory("kscope-process-test")
    private val javaName = if (isWindows()) "java.exe" else "java"
    private val java = Path.of(System.getProperty("java.home"), "bin", javaName).toString()

    private val stub = directory.resolve("Stub.java").also {
        Files.writeString(
            it,
            """
            public class Stub {
                public static void main(String[] args) throws Exception {
                    switch (args[0]) {
                        case "echo" -> {
                            byte[] input = System.in.readAllBytes();
                            System.out.println("stdin=" + input.length + " args=" + args.length + " second=[" + args[1] + "]");
                            System.out.println("env=" + System.getenv("KSCOPE_PROBE") + " blocked=" + System.getenv("CLAUDE_PROBE"));
                        }
                        case "events" -> {
                            System.out.println("{\"n\":1}");
                            System.out.println("{\"n\":2}");
                        }
                        case "sleep" -> Thread.sleep(120000);
                        case "spawn" -> {
                            Process child = new ProcessBuilder(args[1], args[2], "sleep").inheritIO().start();
                            System.out.println("child=" + child.pid());
                            System.out.flush();
                            child.waitFor();
                        }
                        default -> System.exit(3);
                    }
                }
            }
            """.trimIndent(),
        )
    }

    private fun childEnvironment(): Map<String, String> {
        val environment = ChildEnvironment.with(System.getenv(), mapOf("KSCOPE_PROBE" to "visible"))
        return environment.filterKeys { !it.startsWith("CLAUDE") }
    }

    @AfterTest
    fun cleanUp() {
        TemporaryTree.deleteEventually(directory)
    }

    private fun run(mode: String, vararg extra: String, stdin: Path? = null, timeoutSeconds: Long = 60) =
        launch(mode, extra.toList(), stdin, timeoutSeconds)

    private fun launch(mode: String, extra: List<String>, stdin: Path?, timeoutSeconds: Long): ProcessOutcome {
        val command = listOf(java, stub.toString(), mode) + extra
        val spec = ProcessSpec(command, directory, childEnvironment(), stdin, directory.resolve("stderr.txt"))
        return ProcessRunner().run(spec, directory.resolve("stdout-$mode.txt"), Duration.ofSeconds(timeoutSeconds))
    }

    @Test
    fun `a long prompt with newlines and quotes arrives intact on standard input`() {
        val prompt = directory.resolve("prompt.txt")
        val text = (1..4000).joinToString("\n") { "line $it \"quoted\" ${"x".repeat(20)}" }
        Files.writeString(prompt, text)

        val outcome = run("echo", "", stdin = prompt)

        assertEquals(0, outcome.exitCode)
        assertEquals("stdin=${text.toByteArray().size} args=2 second=[]", outcome.lines.first().text)
    }

    @Test
    fun `an empty argument and an argument with spaces keep their identity`() {
        val outcome = run("echo", "two words")

        assertEquals("stdin=0 args=2 second=[two words]", outcome.lines.first().text)
    }

    @Test
    fun `the child sees exactly the given environment`() {
        val outcome = run("echo", "")

        assertEquals("env=visible blocked=null", outcome.lines.last().text)
    }

    @Test
    fun `stream lines are recorded in order with elapsed times and written to the log`() {
        val outcome = run("events")

        assertEquals(listOf("{\"n\":1}", "{\"n\":2}"), outcome.lines.map { it.text })
        assertEquals(listOf(0, 1), outcome.lines.map { it.index })
        assertTrue(outcome.lines.zipWithNext().all { (first, second) -> first.elapsedMs <= second.elapsedMs })
        assertEquals(listOf("{\"n\":1}", "{\"n\":2}"), Files.readAllLines(directory.resolve("stdout-events.txt")))
    }

    @Test
    fun `a run that exceeds its timeout is stopped with its whole process tree`() {
        val outcome = run("spawn", java, stub.toString(), timeoutSeconds = 8)

        assertTrue(outcome.timedOut)
        assertNull(outcome.exitCode)
        val childId = outcome.lines.first { it.text.startsWith("child=") }.text.removePrefix("child=").toLong()
        val child = ProcessHandle.of(childId)
        child.ifPresent { it.onExit().get(CHILD_EXIT_SECONDS, TimeUnit.SECONDS) }
        assertFalse(child.map { it.isAlive }.orElse(false))
    }

    @Test
    fun `an exit code is reported and a missing executable is a start failure`() {
        assertEquals(3, run("unknown").exitCode)
        val command = listOf(directory.resolve("no-such-program").toString())
        val spec = ProcessSpec(command, directory, System.getenv(), null, directory.resolve("e.txt"))
        val missing = ProcessRunner().run(spec, directory.resolve("o.txt"), Duration.ofSeconds(5))

        assertNotNull(missing.startFailure)
        assertNull(missing.exitCode)
    }

    @Test
    fun `an executable is found on the path by name`() {
        val tool = directory.resolve(if (isWindows()) "probe-tool.exe" else "probe-tool")
        Files.writeString(tool, "x")

        val found = ExecutableLocator.find("probe-tool", mapOf("PATH" to directory.toString()))

        assertEquals(tool, found)
        assertNull(ExecutableLocator.find("absent-tool", mapOf("PATH" to directory.toString())))
    }
}
