package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Starts the real worker JVM with the runtime classpath that Gradle passes in and talks to it over stdio. */
class WorkerProcessTest {
    private val stderr: File = File.createTempFile("kscope-worker", ".log")
    private var process: Process? = null
    private val lines = LinkedBlockingQueue<String>()

    @AfterTest
    fun cleanUp() {
        process?.destroyForcibly()
        stderr.delete()
    }

    private fun start(): Process {
        val classpath = checkNotNull(System.getProperty("kscope.worker.classpath")) { "No worker classpath" }
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val started = ProcessBuilder(java, "-Xmx1g", "-cp", classpath, WORKER_MAIN_CLASS)
            .redirectError(stderr)
            .start()
        thread(isDaemon = true) {
            BufferedReader(InputStreamReader(started.inputStream, Charsets.UTF_8)).forEachLine { lines.put(it) }
        }
        process = started
        return started
    }

    private fun send(request: WorkerRequest) {
        val writer = BufferedWriter(OutputStreamWriter(checkNotNull(process).outputStream, Charsets.UTF_8))
        writer.write(WorkerProtocol.encode(request))
        writer.write("\n")
        writer.flush()
    }

    private fun receive(): WorkerResponse {
        val line = lines.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            ?: fail("No response from the worker. stderr:\n${stderr.readText().take(STDERR_LIMIT)}")
        return WorkerProtocol.decodeResponse(line)
    }

    private fun receiveUntilCompleted(): List<WorkerResponse> = buildList {
        do {
            add(receive())
        } while (last() !is WorkerResponse.Completed && last() !is WorkerResponse.Failed)
    }

    private fun assertExits(expectedCode: Int) {
        val finished = checkNotNull(process).waitFor(EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue(finished, "The worker did not exit. stderr:\n${stderr.readText().take(STDERR_LIMIT)}")
        assertEquals(expectedCode, checkNotNull(process).exitValue())
    }

    @Test
    fun `the worker answers a handshake, analyses one module, outlines a script and exits after shutdown`() {
        start()
        send(WorkerRequest.Handshake(WorkerProtocol.PROTOCOL_VERSION))
        assertIs<WorkerResponse.Ready>(receive())

        send(WorkerRequest.Analyze("a-1", listOf(SampleWorkspace.singleModule("shapes", "shapes/main"))))
        val analysis = receiveUntilCompleted()
        val batch = assertIs<WorkerResponse.Batch>(analysis.first())
        assertEquals("sample//:shapes@main", batch.batch.owner.key)
        assertTrue(batch.batch.facts.any { it is Fact.OfSymbol })
        assertEquals(WorkerResponse.Completed("a-1"), analysis.last())

        val script: WorkerFile = SampleWorkspace.file("scripts/settings.gradle.kts")
        send(WorkerRequest.OutlineScripts("o-1", listOf(script)))
        val outline = receiveUntilCompleted()
        assertEquals(script.ref, assertIs<WorkerResponse.Outline>(outline.first()).ref)
        assertEquals(WorkerResponse.Completed("o-1"), outline.last())

        send(WorkerRequest.Shutdown)
        assertExits(0)
        assertFalse(lines.isNotEmpty(), "Nothing but protocol lines may be written to standard output")
    }

    @Test
    fun `the worker exits successfully when its input ends`() {
        start()
        checkNotNull(process).outputStream.close()
        assertExits(0)
    }

    @Test
    fun `an unreadable request is answered with a failure and a non-zero exit`() {
        start()
        checkNotNull(process).outputStream.apply {
            write("not a request\n".toByteArray())
            flush()
        }
        val failed = assertIs<WorkerResponse.Failed>(receive())
        assertEquals("worker.protocol", failed.reason.value)
        assertExits(1)
    }

    private companion object {
        const val WORKER_MAIN_CLASS = "io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerMain"
        const val RESPONSE_TIMEOUT_SECONDS = 180L
        const val EXIT_TIMEOUT_SECONDS = 60L
        const val STDERR_LIMIT = 4000
    }
}
