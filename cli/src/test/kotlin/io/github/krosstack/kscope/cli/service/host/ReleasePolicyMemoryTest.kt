package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import kotlin.test.Test
import kotlin.test.assertEquals

class ReleasePolicyMemoryTest {
    private val heap = FakeHeap()
    private val policy = ReleasePolicy(heap) { 0L }
    private val released = mutableListOf<String>()
    private val limits = ServiceLimits(idleMinutes = 0, memoryMib = 100)

    private fun candidate(name: String, lastRequestAt: Long, loaded: Boolean = true) =
        ReleaseCandidate(loaded, lastRequestAt) { released.add(name) }

    private fun look(vararg candidates: ReleaseCandidate) = policy.look(limits, candidates.toList())

    @Test
    fun `above the limit the loaded workspace used least recently is released and the others stay`() {
        heap.used = 81 * MIB
        heap.collections = 1

        look(
            candidate("a", lastRequestAt = 30),
            candidate("b", lastRequestAt = 10),
            candidate("c", lastRequestAt = 20),
            candidate("unloaded", lastRequestAt = 5, loaded = false),
        )

        assertEquals(listOf("b"), released)
    }

    @Test
    fun `a second workspace is released only after a further collection and while the limit is still exceeded`() {
        heap.used = 90 * MIB
        heap.collections = 1
        look(candidate("a", 30), candidate("b", 10), candidate("c", 20))
        assertEquals(listOf("b"), released)

        look(candidate("a", 30), candidate("b", 10, loaded = false), candidate("c", 20))
        assertEquals(listOf("b"), released)

        heap.collections = 2
        heap.used = 70 * MIB
        look(candidate("a", 30), candidate("b", 10, loaded = false), candidate("c", 20))
        assertEquals(listOf("b"), released)

        heap.used = 90 * MIB
        heap.collections = 3
        look(candidate("a", 30), candidate("b", 10, loaded = false), candidate("c", 20))
        assertEquals(listOf("b", "c"), released)
    }

    @Test
    fun `nothing is released while the bytes in use do not exceed the limit`() {
        heap.used = 80 * MIB
        heap.collections = 1

        look(candidate("a", 10))

        assertEquals(emptyList(), released)
    }

    @Test
    fun `nothing happens when no workspace is loaded, and the first one that is loaded is released at once`() {
        heap.used = 90 * MIB
        heap.collections = 1

        look(candidate("a", 10, loaded = false))
        look()
        assertEquals(emptyList(), released)

        look(candidate("b", 10))
        assertEquals(listOf("b"), released)
    }

    @Test
    fun `the limit is 80 percent of the smaller of the key and the maximum heap`() {
        val cases = listOf(
            Triple(ServiceLimits(memoryMib = 100), 1_024 * MIB, 80 * MIB),
            Triple(ServiceLimits(memoryMib = 2_000), 100 * MIB, 80 * MIB),
            Triple(ServiceLimits(memoryMib = 0), 500 * MIB, 400 * MIB),
            Triple(ServiceLimits(), 500 * MIB, 400 * MIB),
        )
        for ((key, maximum, ceiling) in cases) {
            for (offset in listOf(0L, 1L)) {
                heap.maximum = maximum
                heap.used = ceiling + offset
                heap.collections = 0
                released.clear()

                ReleasePolicy(heap) { 0L }.look(key.copy(idleMinutes = 0), listOf(candidate("a", 10)))

                val expected = if (offset == 0L) emptyList() else listOf("a")
                assertEquals(expected, released, "$key, maximum heap $maximum, used ${heap.used}")
            }
        }
    }
}
