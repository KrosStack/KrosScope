package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServerAlreadyRunning
import java.nio.file.Files
import java.nio.file.Path

internal object WorkspaceFailures {
    fun stateOf(path: Path, failure: Throwable): WorkspaceState = when {
        Files.notExists(path) -> WorkspaceState.NotServed("the directory does not exist")
        failure is ServerAlreadyRunning -> WorkspaceState.Waiting
        else -> WorkspaceState.NotServed(describe(failure))
    }

    fun describe(failure: Throwable): String = when (failure) {
        is OutOfMemoryError -> "it does not fit in memory"
        is Error -> listOfNotNull(failure.javaClass.simpleName, failure.message).joinToString(": ")
        else -> failure.message ?: failure.javaClass.simpleName
    }
}
