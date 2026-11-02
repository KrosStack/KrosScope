package io.github.krosstack.kscope.cli.service.entry

internal class RecordingRunner(
    private val answer: (List<String>) -> CommandOutcome = { CommandOutcome.Finished(0, "") },
) : CommandRunner {
    val commands = mutableListOf<List<String>>()

    override fun run(command: List<String>): CommandOutcome {
        commands += command
        return answer(command)
    }
}

internal fun failing(vararg names: String, outcome: CommandOutcome = CommandOutcome.Finished(1, "failed")) =
    RecordingRunner { command -> if (names.any { it in command }) outcome else CommandOutcome.Finished(0, "") }
