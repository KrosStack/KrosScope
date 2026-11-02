package io.github.krosstack.kscope.cli.service.host

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceLooksTest {
    @Test
    fun `it looks again after each period until it is closed`() {
        val third = CountDownLatch(3)
        val looks = AtomicInteger()
        val repeating = ServiceLooks(periodMillis = 1) {
            looks.incrementAndGet()
            third.countDown()
        }
        repeating.start()

        assertTrue(third.await(WAIT_SECONDS, TimeUnit.SECONDS))
        repeating.close()

        val after = looks.get()
        assertTrue(after >= 3, "$after looks")
        assertEquals(after, looks.get())
    }

    @Test
    fun `a look that fails does not end the looking`() {
        val second = CountDownLatch(2)
        val failing = ServiceLooks(periodMillis = 1) {
            second.countDown()
            error("a look failed")
        }
        failing.start()

        assertTrue(second.await(WAIT_SECONDS, TimeUnit.SECONDS))
        failing.close()
    }

    @Test
    fun `closing ends a thread that is waiting for a long period at once`() {
        val looks = AtomicInteger()
        val patient = ServiceLooks(periodMillis = Long.MAX_VALUE) { looks.incrementAndGet() }
        patient.start()

        patient.close()

        assertEquals(0, looks.get())
    }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
