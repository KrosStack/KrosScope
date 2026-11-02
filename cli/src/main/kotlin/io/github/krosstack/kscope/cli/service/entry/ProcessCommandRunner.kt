package io.github.krosstack.kscope.cli.service.entry

import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal class ProcessCommandRunner(private val timeoutMillis: Long = TIMEOUT_MILLIS) : CommandRunner {
    override fun run(command: List<String>): CommandOutcome = try {
        execute(command)
    } catch (failure: IOException) {
        CommandOutcome.Unfinished(failure.message.orEmpty())
    }

    private fun execute(command: List<String>): CommandOutcome {
        val process = ProcessBuilder(command)
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .redirectErrorStream(true)
            .start()
        val output = collect(process)
        return if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            val text = output.completeOnTimeout("", READ_GRACE_SECONDS, TimeUnit.SECONDS).join()
            CommandOutcome.Finished(process.exitValue(), text)
        } else {
            process.destroyForcibly()
            CommandOutcome.Unfinished("timed out after ${timeoutMillis / MILLIS_PER_SECOND} seconds")
        }
    }

    private fun collect(process: Process): CompletableFuture<String> {
        val output = CompletableFuture<String>()
        Thread({ output.complete(read(process)) }, "kscope-command-output").apply {
            isDaemon = true
            start()
        }
        return output
    }

    private fun read(process: Process): String = try {
        process.inputStream.readAllBytes().decodeToString()
    } catch (_: IOException) {
        ""
    }

    private fun nullDevice(): File =
        File(if (System.getProperty("os.name").startsWith("Windows")) "NUL" else "/dev/null")

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val READ_GRACE_SECONDS = 5L
    }
}
