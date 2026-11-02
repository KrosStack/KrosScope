package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.worker.WorkerModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Starts the fake worker in a JVM of its own for a test and ends every worker that the test leaves running. */
internal class FakeWorkerHarness : AutoCloseable {
    private val directory: Path = Files.createTempDirectory("kscope-fake-worker")
    private val pidFile: Path = directory.resolve("pid")
    private val opened = CopyOnWriteArrayList<KotlinWorkerProcess>()
    private val arrivals = LinkedBlockingQueue<String>()

    /** Every line the worker wrote to standard error or outside the protocol. */
    val diagnostics = CopyOnWriteArrayList<String>()

    fun command(mode: String) = WorkerCommand.Runnable(
        listOf(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xmx64m",
            "-cp",
            checkNotNull(System.getProperty("kscope.test.classpath")),
            FakeWorkerMain::class.java.name,
            mode,
            pidFile.toString(),
        ),
    )

    fun launcher(mode: String, timeouts: WorkerTimeouts = PATIENT) = WorkerLauncher({ command(mode) }, timeouts)

    fun start(mode: String, timeouts: WorkerTimeouts = PATIENT): WorkerStart {
        val started = launcher(mode, timeouts).start { line ->
            diagnostics += line
            arrivals.put(line)
        }
        if (started is WorkerStart.Started) opened += started.process
        return started
    }

    fun started(mode: String, timeouts: WorkerTimeouts = PATIENT) =
        assertIs<WorkerStart.Started>(start(mode, timeouts)).process

    fun unavailable(mode: String, timeouts: WorkerTimeouts = PATIENT) =
        assertIs<WorkerStart.Unavailable>(start(mode, timeouts)).reason

    fun analyse(process: KotlinWorkerProcess, vararg modules: WorkerModule): Pair<WorkerOutcome, List<FactBatch>> {
        val received = mutableListOf<FactBatch>()
        return process.analyze(modules.toList()) { received += it } to received
    }

    fun awaitDiagnostic(line: String): Boolean {
        while (true) {
            val next = arrivals.poll(AWAIT_SECONDS, TimeUnit.SECONDS) ?: return false
            if (next == line) return true
        }
    }

    fun assertProcessEnded() {
        val pid = Files.readString(pidFile).toLong()
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), "The worker $pid is still running")
    }

    override fun close() {
        opened.forEach(KotlinWorkerProcess::close)
        directory.toFile().deleteRecursively()
    }

    companion object {
        val PATIENT = WorkerTimeouts(handshake = 1.minutes, response = 1.minutes, shutdown = 10.seconds)
        private const val AWAIT_SECONDS = 10L
    }
}
