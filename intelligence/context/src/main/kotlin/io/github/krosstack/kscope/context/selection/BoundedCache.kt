package io.github.krosstack.kscope.context.selection

internal class BoundedCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val maxWeight: Long,
    private val weigh: (K) -> Int,
) {
    private val entries = LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var weight = 0L

    fun getOrCompute(key: K, compute: () -> V): V {
        entries[key]?.let { return it }
        val value = compute()
        val size = weigh(key)
        if (size <= maxWeight) {
            entries[key] = value
            weight += size
            evict()
        }
        return value
    }

    private fun evict() {
        while (entries.size > maxEntries || weight > maxWeight) {
            val iterator = entries.entries.iterator()
            weight -= weigh(iterator.next().key)
            iterator.remove()
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
