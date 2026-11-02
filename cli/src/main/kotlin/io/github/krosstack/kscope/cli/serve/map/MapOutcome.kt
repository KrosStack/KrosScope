package io.github.krosstack.kscope.cli.serve.map

internal sealed interface MapOutcome<out T> {
    data class Done<T>(val value: T) : MapOutcome<T>

    data class Failed(val failure: Throwable) : MapOutcome<Nothing>

    data object TimedOut : MapOutcome<Nothing>
}
