package io.github.krosstack.kscope.service.map.edges

import io.github.krosstack.kscope.service.map.tree.NONE

/** Lists of items per node, kept in two arrays. The items of a node stay in the order they were given. */
internal class NodeLists private constructor(private val starts: IntArray, private val items: IntArray) {
    fun size(node: Int): Int = starts[node + 1] - starts[node]

    fun items(node: Int, limit: Int): IntArray {
        val start = starts[node]
        return items.copyOfRange(start, start + minOf(size(node), limit))
    }

    companion object {
        /** [keys] holds the node of each item, or [NONE] for an item that belongs to none. */
        fun group(nodeCount: Int, keys: IntArray): NodeLists {
            val starts = IntArray(nodeCount + 1)
            keys.forEach { if (it >= 0) starts[it + 1]++ }
            for (node in 0 until nodeCount) starts[node + 1] += starts[node]
            val next = starts.copyOf(nodeCount)
            val items = IntArray(starts[nodeCount])
            keys.forEachIndexed { item, node -> if (node >= 0) items[next[node]++] = item }
            return NodeLists(starts, items)
        }
    }
}
