package io.github.krosstack.kscope.engine.discovery

import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal sealed interface ProcessResult {
    class Completed(val exitCode: Int, val output: ByteArray) : ProcessResult

    /** The process did not start, did not finish in time or was interrupted. */
    class Incomplete(val reason: String) : ProcessResult
}

/** The single place where external processes are started. Standard error is discarded. */
internal class ProcessRunner(
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val onStart: (List<String>) -> Unit = {},
) {
    fun run(command: List<String>, workingDirectory: Path): ProcessResult {
        val process = try {
            ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (failure: IOException) {
            return ProcessResult.Incomplete("${command.first()} could not be started: ${failure.message}")
        }
        onStart(command)
        process.outputStream.close()
        val output = CompletableFuture.supplyAsync { process.inputStream.readAllBytes() }
        return try {
            awaitCompletion(process, output)
        } catch (interruption: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            ProcessResult.Incomplete("${command.first()} was interrupted: ${interruption.message}")
        } catch (failure: ExecutionException) {
            process.destroyForcibly()
            ProcessResult.Incomplete("${command.first()} output could not be read: ${failure.message}")
        } catch (expired: TimeoutException) {
            process.destroyForcibly()
            ProcessResult.Incomplete("${command.first()} did not finish within $timeout: ${expired.message}")
        }
    }

    private fun awaitCompletion(process: Process, output: CompletableFuture<ByteArray>): ProcessResult {
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw TimeoutException("the process kept running")
        }
        val bytes = output.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        return ProcessResult.Completed(process.exitValue(), bytes)
    }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
