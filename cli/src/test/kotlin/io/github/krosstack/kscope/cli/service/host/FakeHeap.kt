package io.github.krosstack.kscope.cli.service.host

internal const val MIB = 1_048_576L

internal class FakeHeap(var used: Long = 0, var collections: Long = 0, var maximum: Long = 1_024 * MIB) :
    HeapStatistics {
    override val maximumBytes: Long get() = maximum

    override fun reading() = HeapReading(used, collections)
}
