package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.TemporaryTree
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertIs

class ProcessRunnerTest {
    @Test
    fun `a process that outlives its timeout is reported as incomplete`() {
        val directory = Files.createTempDirectory("kscope-timeout")
        try {
            val source = directory.resolve("Sleep.java")
            val program = "public class Sleep { public static void main(String[] a) throws Exception " +
                "{ Thread.sleep(60000); } }"
            Files.writeString(source, program)
            val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
            val result = ProcessRunner(Duration.ofMillis(300)).run(listOf(java, source.toString()), directory)
            assertIs<ProcessResult.Incomplete>(result)
        } finally {
            TemporaryTree.deleteEventually(directory)
        }
    }

    @Test
    fun `a process that cannot be started is reported as incomplete`() {
        val result = ProcessRunner().run(listOf("kscope-no-such-executable"), Path.of("."))
        assertIs<ProcessResult.Incomplete>(result)
    }
}
