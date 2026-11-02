package io.github.krosstack.kscope.cli.service.host

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class ServiceLooks(private val periodMillis: Long, private val look: () -> Unit) : AutoCloseable {
    private val closing = CountDownLatch(1)
    private val thread = Thread(::lookUntilClosed, "kscope-service-limits").apply { isDaemon = true }

    fun start() = thread.start()

    override fun close() {
        closing.countDown()
        thread.join()
    }

    // A look that fails is made again at the next period: it must not end the thread that watches the memory.
    private fun lookUntilClosed() {
        while (!closing.await(periodMillis, TimeUnit.MILLISECONDS)) runCatching(look)
    }
}
