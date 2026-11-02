package io.github.krosstack.kscope.cli.service.entry

internal fun interface CommandRunner {
    fun run(command: List<String>): CommandOutcome
}
