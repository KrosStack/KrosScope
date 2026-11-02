package io.github.krosstack.kscope.context.selection

internal class PresentFiles {
    private val slots = ArrayList<FileSlot>()

    fun add(slot: FileSlot) {
        slots.add(insertionIndex(slot), slot)
    }

    fun rendered(): List<RenderedFile> = slots.map(::renderedOf)

    fun arranged(slot: FileSlot, rendered: RenderedFile): List<RenderedFile> {
        val files = slots.mapTo(ArrayList<RenderedFile>(slots.size + 1), ::renderedOf)
        val index = insertionIndex(slot)
        if (slot.isPresent) files[index] = rendered else files.add(index, rendered)
        return files
    }

    fun totalValue(): Double = valueAcross(0, slots.size, 0.0)

    // Addition of doubles is not associative: files in rank order and values in render order, with the proposed
    // file between its neighbours, as in every earlier version, so that equal selections have bitwise equal gains
    // and the same ties.
    fun valueWith(slot: FileSlot, rendered: RenderedFile): Double {
        val index = insertionIndex(slot)
        val including = accumulate(valueAcross(0, index, 0.0), rendered.values)
        return valueAcross(if (slot.isPresent) index + 1 else index, slots.size, including)
    }

    private fun valueAcross(first: Int, end: Int, start: Double): Double {
        var sum = start
        for (index in first until end) sum = accumulate(sum, renderedOf(slots[index]).values)
        return sum
    }

    private fun accumulate(start: Double, values: DoubleArray): Double {
        var sum = start
        for (value in values) sum += value
        return sum
    }

    private fun insertionIndex(slot: FileSlot): Int {
        val found = slots.binarySearch { it.rank.compareTo(slot.rank) }
        return if (found >= 0) found else -(found + 1)
    }
}

private fun renderedOf(slot: FileSlot): RenderedFile = requireNotNull(slot.rendered)
