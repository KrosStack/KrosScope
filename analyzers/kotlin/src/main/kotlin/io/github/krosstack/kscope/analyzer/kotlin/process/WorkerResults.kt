package io.github.krosstack.kscope.analyzer.kotlin.process

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [handshake] bounds the wait for the first answer, [response] the silence between two answers of a running request,
 * and [shutdown] the wait for a clean exit before the process is destroyed.
 */
internal class WorkerTimeouts(val handshake: Duration, val response: Duration, val shutdown: Duration) {
    companion object {
        val DEFAULT = WorkerTimeouts(handshake = 1.minutes, response = 10.minutes, shutdown = 10.seconds)
    }
}

internal sealed interface WorkerStart {
    class Started(val process: KotlinWorkerProcess) : WorkerStart

    class Unavailable(val reason: String) : WorkerStart
}

internal sealed interface WorkerOutcome {
    data object Completed : WorkerOutcome

    class Failed(val message: String) : WorkerOutcome
}
