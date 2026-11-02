package io.github.krosstack.kscope.cli.service.home

import io.github.krosstack.kscope.cli.service.inServiceHome
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceFileLockTest {
    private fun awaitWaiting(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (thread.state != Thread.State.WAITING) {
            check(System.nanoTime() < deadline) { "${thread.name} never waited; it is ${thread.state}" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `a second change waits until the first has finished`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val order = CopyOnWriteArrayList<String>()
        val first = Thread({
            file.exclusively {
                order += "first in"
                inside.countDown()
                check(release.await(WAIT_SECONDS, TimeUnit.SECONDS))
                order += "first out"
            }
        }, "first")
        val second = Thread({ file.exclusively { order += "second in" } }, "second")

        first.start()
        assertTrue(inside.await(WAIT_SECONDS, TimeUnit.SECONDS))
        second.start()
        awaitWaiting(second)
        release.countDown()
        first.join()
        second.join()

        assertEquals(listOf("first in", "first out", "second in"), order)
    }

    @Test
    fun `the lock is released when the change fails`() = inServiceHome { home ->
        val file = ServiceFile(home.resolve("service.toml"))

        val failure = runCatching { file.exclusively { error("the change failed") } }.exceptionOrNull()

        assertEquals("the change failed", failure?.message)
        assertEquals("fine", file.exclusively { "fine" })
    }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
