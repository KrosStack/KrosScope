package io.github.krosstack.kscope.cli.status

import java.time.Instant

internal class ProcessFacts(val alive: Boolean, val startedAt: Instant?)

internal fun interface RunningProcesses {
    fun find(pid: Long): ProcessFacts?

    companion object {
        val SYSTEM = RunningProcesses { pid ->
            ProcessHandle.of(pid).map { ProcessFacts(it.isAlive, it.info().startInstant().orElse(null)) }.orElse(null)
        }
    }
}
