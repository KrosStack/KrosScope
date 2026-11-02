package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.Writer

/** Writes each response as one line and flushes, so the client never waits on buffered output. */
internal class ResponseWriter(private val out: Writer) {
    @Synchronized
    fun send(response: WorkerResponse) {
        out.write(WorkerProtocol.encode(response))
        out.write("\n")
        out.flush()
    }
}
