package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.WorkerSettings

/** Starts a worker for one piece of work; every call starts a new JVM. */
internal class WorkerLauncher(
    private val command: () -> WorkerCommand,
    private val timeouts: WorkerTimeouts = WorkerTimeouts.DEFAULT,
) {
    fun start(onDiagnostic: (String) -> Unit): WorkerStart = when (val found = command()) {
        is WorkerCommand.Missing -> WorkerStart.Unavailable(found.reason)
        is WorkerCommand.Runnable -> KotlinWorkerProcess.start(found.arguments, timeouts, onDiagnostic)
    }

    companion object {
        fun fromEnvironment(settings: () -> WorkerSettings = { WorkerSettings.NONE }): WorkerLauncher =
            WorkerLauncher(command = { WorkerLocator.system().command(settings()) })
    }
}
