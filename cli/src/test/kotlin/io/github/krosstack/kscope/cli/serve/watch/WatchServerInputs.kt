package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.model.Fingerprint
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class WatchServerInputs {
    val clock = AtomicLong()
    val stamp = AtomicReference(Fingerprint("initial"))
    val git = AtomicBoolean()
    val trusted = AtomicBoolean()
    val shallow = AtomicBoolean()
    val shallowAnswers = ConcurrentLinkedQueue<Boolean>()
    val reads = AtomicInteger()
    val firstPoll = CountDownLatch(1)

    fun settings(idleMillis: Long = 0): ServeSettings = ServeSettings(
        idleTimeoutMillis = idleMillis,
        watchEnabled = true,
        clock = clock::get,
        watch = WatchSettings(
            timing = WatchTiming(pollMillis = 60_000),
            stamp = {
                reads.incrementAndGet()
                stamp.get()
            },
            git = { { git.get() } },
            onPoll = { firstPoll.countDown() },
            requiresTrust = { trusted.get() },
            isShallow = { shallowAnswers.poll() ?: shallow.get() },
        ),
    )

    fun awaitFirstPoll() = assertTrue(firstPoll.await(5, TimeUnit.SECONDS))

    fun poll(server: ServeServer, seconds: Long, value: String = "changed") {
        stamp.set(Fingerprint(value))
        clock.set(TimeUnit.SECONDS.toNanos(seconds))
        assertNotNull(server.watchState).poll()
    }

    /**
     * Makes the index stay shallow after [attempts] automatic scans that succeed, at the times the backoff allows:
     * 4, 14, 34, 74 and 154 seconds. A served query after each look waits until the scan it started has ended.
     */
    fun leaveShallow(server: ServeServer, attempts: Int) {
        shallow.set(true)
        for (second in ATTEMPT_SECONDS.take(attempts)) {
            poll(server, second - QUIET_SECONDS, "initial")
            poll(server, second, "initial")
            assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)
        }
    }

    private companion object {
        const val QUIET_SECONDS = 4L
        val ATTEMPT_SECONDS = listOf(4L, 14L, 34L, 74L, 154L)
    }
}
