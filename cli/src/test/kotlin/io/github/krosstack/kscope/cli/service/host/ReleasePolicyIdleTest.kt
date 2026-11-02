package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

class ReleasePolicyIdleTest {
    private val heap = FakeHeap()
    private val clock = AtomicLong()
    private val policy = ReleasePolicy(heap, clock::get)
    private val released = mutableListOf<String>()

    private fun minutes(count: Long) = TimeUnit.MINUTES.toNanos(count)

    private fun candidate(name: String, lastRequestAt: Long, loaded: Boolean = true) =
        ReleaseCandidate(loaded, lastRequestAt) { released.add(name) }

    @Test
    fun `a workspace that was idle for the limit is released and one that was idle for less is not`() {
        clock.set(minutes(10))

        policy.look(
            ServiceLimits(idleMinutes = 10),
            listOf(
                candidate("idle", lastRequestAt = 0),
                candidate("just under", lastRequestAt = 1),
                candidate("recent", lastRequestAt = minutes(5)),
                candidate("unloaded", lastRequestAt = 0, loaded = false),
            ),
        )

        assertEquals(listOf("idle"), released)
    }

    @Test
    fun `an idle limit of 0 never releases and an absent key means 30 minutes`() {
        clock.set(minutes(1_000))

        policy.look(ServiceLimits(idleMinutes = 0), listOf(candidate("a", 0)))
        assertEquals(emptyList(), released)

        clock.set(minutes(30) - 1)
        policy.look(ServiceLimits(), listOf(candidate("b", 0)))
        assertEquals(emptyList(), released)

        clock.set(minutes(30))
        policy.look(ServiceLimits(), listOf(candidate("c", 0)))
        assertEquals(listOf("c"), released)
    }

    @Test
    fun `the idle time is measured from the last request in the clock of the server`() {
        val oneMinute = ServiceLimits(idleMinutes = 1)
        clock.set(TimeUnit.SECONDS.toNanos(100))

        policy.look(oneMinute, listOf(candidate("a", lastRequestAt = TimeUnit.SECONDS.toNanos(41))))
        policy.look(oneMinute, listOf(candidate("b", lastRequestAt = TimeUnit.SECONDS.toNanos(40))))

        assertEquals(listOf("b"), released)
    }

    @Test
    fun `workspaces released for being idle leave the memory step for the next look`() {
        val limits = ServiceLimits(idleMinutes = 5, memoryMib = 100)
        clock.set(minutes(10))
        heap.used = 90 * MIB
        heap.collections = 1

        policy.look(limits, listOf(candidate("idle", lastRequestAt = 0), candidate("busy", minutes(9))))
        assertEquals(listOf("idle"), released)

        policy.look(limits, listOf(candidate("busy", lastRequestAt = minutes(9))))
        assertEquals(listOf("idle", "busy"), released)
    }
}
