package io.github.krosstack.kscope.service.freshness

internal class StartedProcesses {
    private val processes = ArrayList<Process>()
    private var stopped = false

    @Synchronized
    fun add(process: Process) {
        if (stopped) {
            process.destroyForcibly()
        } else {
            processes += process
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        processes.forEach { it.destroyForcibly() }
    }
}
