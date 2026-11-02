package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.discovery.ProcessResult
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal fun interface GitIgnoreRunner {
    fun run(command: List<String>, workingDirectory: Path, input: ByteArray): ProcessResult
}

internal class GitIgnoreProcessRunner : GitIgnoreRunner {
    override fun run(command: List<String>, workingDirectory: Path, input: ByteArray): ProcessResult {
        val process = try {
            ProcessBuilder(command).directory(workingDirectory.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (_: IOException) {
            return ProcessResult.Incomplete("Git could not be started")
        }
        val output = CompletableFuture.supplyAsync { process.inputStream.readAllBytes() }
        val writing = CompletableFuture.supplyAsync { process.outputStream.use { it.write(input) } }
        return try {
            awaitCompletion(process, output, writing)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            ProcessResult.Incomplete("Git was interrupted")
        } catch (_: ExecutionException) {
            process.destroyForcibly()
            ProcessResult.Incomplete("Git input or output could not be transferred")
        } catch (_: TimeoutException) {
            process.destroyForcibly()
            ProcessResult.Incomplete("Git did not finish in time")
        }
    }

    private fun awaitCompletion(
        process: Process,
        output: CompletableFuture<ByteArray>,
        writing: CompletableFuture<Unit>,
    ): ProcessResult {
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw TimeoutException("the Git process kept running")
        }
        writing.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return ProcessResult.Completed(process.exitValue(), output.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private companion object {
        const val TIMEOUT_SECONDS = 60L
    }
}
