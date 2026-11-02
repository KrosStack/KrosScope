package io.github.krosstack.kscope.benchmark.agent.launch

import io.github.krosstack.kscope.benchmark.agent.telemetry.TimedLine
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class ProcessSpec(
    val command: List<String>,
    val directory: Path,
    val environment: Map<String, String>,
    val stdin: Path?,
    val stderr: Path,
)

internal class ProcessOutcome(
    val exitCode: Int?,
    val timedOut: Boolean,
    val wallMs: Long,
    val lines: List<TimedLine>,
    val startFailure: String? = null,
)

internal class ProcessRunner {
    fun run(spec: ProcessSpec, stdout: Path, timeout: Duration): ProcessOutcome {
        val startedAt = System.nanoTime()
        val builder = ProcessBuilder(spec.command).directory(spec.directory.toFile())
        builder.environment().clear()
        builder.environment().putAll(spec.environment)
        spec.stdin?.let { builder.redirectInput(it.toFile()) }
        builder.redirectError(spec.stderr.toFile())
        val process = try {
            builder.start()
        } catch (error: IOException) {
            return ProcessOutcome(null, false, 0, emptyList(), error.message ?: "start failed")
        }
        LiveProcesses.add(process)
        if (spec.stdin == null) process.outputStream.close()
        val pump = LinePump(process.inputStream, stdout, startedAt)
        val finished = process.waitFor(timeout.toMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS)
        if (!finished) LiveProcesses.terminate(process)
        process.waitFor(GRACE_SECONDS, TimeUnit.SECONDS)
        val lines = pump.finish()
        LiveProcesses.remove(process)
        val wallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        return ProcessOutcome(if (finished) process.exitValue() else null, !finished, wallMs, lines)
    }

    fun capture(
        command: List<String>,
        directory: Path,
        environment: Map<String, String>,
        input: Path? = null,
    ): Captured {
        val output = Files.createTempFile("kscope-capture", ".out")
        val errors = Files.createTempFile("kscope-capture", ".err")
        try {
            val builder = ProcessBuilder(command).directory(directory.toFile())
            builder.environment().clear()
            builder.environment().putAll(environment)
            input?.let { builder.redirectInput(it.toFile()) }
            val process = builder.redirectOutput(output.toFile()).redirectError(errors.toFile()).start()
            LiveProcesses.add(process)
            if (input == null) process.outputStream.close()
            val exit = process.waitFor()
            LiveProcesses.remove(process)
            return Captured(exit, Files.readString(output), Files.readString(errors))
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(errors)
        }
    }

    private companion object {
        const val GRACE_SECONDS = 15L
    }
}

internal class Captured(val exitCode: Int, val stdout: String, val stderr: String)

private object LiveProcesses {
    private val processes = ConcurrentHashMap.newKeySet<Process>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread { processes.forEach(::terminate) })
    }

    fun add(process: Process) {
        processes += process
    }

    fun remove(process: Process) {
        processes -= process
    }

    fun terminate(process: Process) {
        val handle = process.toHandle()
        handle.descendants().forEach { it.destroyForcibly() }
        handle.destroyForcibly()
    }
}

private class LinePump(stream: InputStream, target: Path, private val startedAt: Long) {
    private val lines = mutableListOf<TimedLine>()
    private val thread = Thread { pump(stream, target) }.apply {
        isDaemon = true
        start()
    }

    private fun pump(stream: InputStream, target: Path) {
        try {
            Files.newBufferedWriter(target).use { writer ->
                stream.bufferedReader(Charsets.UTF_8).useLines { sequence -> sequence.forEach { record(it, writer) } }
            }
        } catch (_: IOException) {
            return
        } catch (_: UncheckedIOException) {
            return
        }
    }

    private fun record(text: String, writer: BufferedWriter) {
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        synchronized(lines) { lines += TimedLine(lines.size, elapsed, text) }
        writer.write(text)
        writer.newLine()
    }

    fun finish(): List<TimedLine> {
        thread.join(JOIN_MILLIS)
        return synchronized(lines) { lines.toList() }
    }

    private companion object {
        const val JOIN_MILLIS = 15_000L
    }
}
