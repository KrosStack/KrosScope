package io.github.krosstack.kscope.cli.service.entry

import java.nio.file.Path

internal sealed interface CommandOutcome {
    val succeeded: Boolean

    data class Finished(val exitCode: Int, val output: String) : CommandOutcome {
        override val succeeded: Boolean get() = exitCode == 0
    }

    data class Unfinished(val reason: String) : CommandOutcome {
        override val succeeded: Boolean get() = false
    }
}

internal fun CommandOutcome.failureOf(command: List<String>): String {
    val name = (listOf(Path.of(command.first()).fileName.toString()) + command.drop(1)).joinToString(" ")
    return when (this) {
        is CommandOutcome.Finished -> "$name failed with exit code $exitCode." + output.trim().prependLine()
        is CommandOutcome.Unfinished -> "$name failed: $reason."
    }
}

private fun String.prependLine(): String = if (isEmpty()) "" else "\n$this"
