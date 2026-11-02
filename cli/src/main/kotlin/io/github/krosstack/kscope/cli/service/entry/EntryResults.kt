package io.github.krosstack.kscope.cli.service.entry

internal sealed interface Installation {
    data object Installed : Installation

    data class Refused(val message: String) : Installation
}

internal sealed interface Removal {
    data object Removed : Removal

    data object NotInstalled : Removal

    data class Refused(val message: String) : Removal
}

internal sealed interface EntryState {
    data object NotRegistered : EntryState

    data class Registered(val command: RegisteredCommand?) : EntryState

    data class Disabled(val unit: String) : EntryState
}
