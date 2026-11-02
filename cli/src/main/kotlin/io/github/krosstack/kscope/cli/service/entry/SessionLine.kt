package io.github.krosstack.kscope.cli.service.entry

internal object SessionLine {
    const val STARTS = "It starts with your session."
    const val NOT_REGISTERED = "It does not start with your session; `kscope service install` registers it."
    private const val REGISTERED = "It is registered to start with your session, but"
    private const val AGAIN = "run `kscope service install` again."

    fun of(state: EntryState): String = when (state) {
        EntryState.NotRegistered -> NOT_REGISTERED
        is EntryState.Registered -> registered(state.command)
        is EntryState.Disabled -> "$REGISTERED the unit ${state.unit} is not enabled; $AGAIN"
    }

    private fun registered(command: RegisteredCommand?): String = when (command) {
        null -> "$REGISTERED its command is not the one `kscope service install` writes; $AGAIN"
        else -> command.missing()?.let { "$REGISTERED $it does not exist; $AGAIN" } ?: STARTS
    }
}
