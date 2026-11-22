package io.github.krosstack.kscope.service.semantic.process

import java.io.InputStream
import java.util.concurrent.CountDownLatch

internal class StalledStderrStream : InputStream() {
    private val closed = CountDownLatch(1)

    override fun read(): Int {
        closed.await()
        return -1
    }

    override fun available(): Int = if (closed.count == 0L) 0 else 1

    override fun close() = closed.countDown()
}
