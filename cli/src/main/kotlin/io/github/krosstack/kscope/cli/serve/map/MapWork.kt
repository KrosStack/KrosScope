package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.serve.ServeServices
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** The queue through which the threads of the map ask the serve thread for what only that thread may do. */
internal class MapWork : AutoCloseable {
    private val queue = ConcurrentLinkedQueue<MapJob<*>>()
    private val closed = AtomicBoolean()

    fun <T> run(timeoutMillis: Long, work: (ServeServices) -> T): MapOutcome<T> {
        if (closed.get()) return MapOutcome.TimedOut
        val job = MapJob(work)
        queue.add(job)
        return job.await(timeoutMillis).also { queue.remove(job) }
    }

    fun drain(services: ServeServices) {
        while (true) {
            val job = queue.poll() ?: return
            job.run(services)
        }
    }

    override fun close() {
        closed.set(true)
        generateSequence { queue.poll() }.forEach { it.cancel() }
    }
}
