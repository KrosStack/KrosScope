package io.github.krosstack.kscope.analyzer.history

import java.io.IOException
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal sealed interface GitResult {
    class Completed(val exitCode: Int, val output: String) : GitResult

    /** The process did not start, did not finish in time or was interrupted. */
    class Incomplete(val reason: String) : GitResult
}

/** Runs the Git executable in a directory and waits for it at most [timeout]. Standard error is discarded. */
internal class GitProcess(
    private val executable: String = DEFAULT_EXECUTABLE,
    private val timeout: Duration = TIMEOUT,
) {
    fun run(directory: Path, arguments: List<String>): GitResult {
        val process = try {
            ProcessBuilder(listOf(executable) + OUTPUT_SETTINGS + arguments)
                .directory(directory.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (failure: IOException) {
            return GitResult.Incomplete("$executable could not be started: ${failure.message}")
        }
        process.outputStream.close()
        val output = CompletableFuture.supplyAsync { process.inputStream.readAllBytes() }
        return try {
            awaitCompletion(process, output)
        } catch (interruption: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            GitResult.Incomplete("$executable was interrupted: ${interruption.message}")
        } catch (failure: ExecutionException) {
            process.destroyForcibly()
            GitResult.Incomplete("$executable output could not be read: ${failure.message}")
        } catch (expired: TimeoutException) {
            process.destroyForcibly()
            GitResult.Incomplete("$executable did not finish within $timeout: ${expired.message}")
        }
    }

    private fun awaitCompletion(process: Process, output: CompletableFuture<ByteArray>): GitResult {
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw TimeoutException("the process kept running")
        }
        val bytes = output.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        return GitResult.Completed(process.exitValue(), String(bytes, Charsets.UTF_8))
    }

    companion object {
        const val DEFAULT_EXECUTABLE = "git"

        private val OUTPUT_SETTINGS = listOf("-c", "core.quotepath=off", "-c", "log.showSignature=false")

        private val TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
