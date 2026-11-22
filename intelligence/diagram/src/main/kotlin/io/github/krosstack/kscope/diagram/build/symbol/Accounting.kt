package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SymbolId

private val readTwice = setOf(Unseen.WITHIN_TYPE, Unseen.STOPPED)

private class Tally {
    var served = 0
    var shown = 0
    var reason: Unseen? = null

    fun note(found: Unseen?) {
        val kept = reason
        if (found != null && (kept == null || found < kept)) reason = found
    }
}

private class Rows {
    val tallies = HashMap<Relationship, Tally>()

    fun serve(origin: Relationship): Tally = tallies.getOrPut(origin) { Tally() }.also { it.served += 1 }
}

internal class Accounting(
    private val ends: Ends,
    private val leaving: Map<SymbolId, Reading>,
    private val entering: Map<SymbolId, Reading>,
) {
    private val lost = sortedMapOf<Unseen, Int>()

    fun unseen(drawn: List<FlowLink>): Map<Unseen, Int> {
        val outward = Rows()
        val inward = Rows()
        for ((unit, reading) in leaving) sift(reading, outward) { ends.leaving(unit, it) }
        for (reading in entering.values) sift(reading, inward) { ends.entering(it) }
        for (link in drawn.filter { ends.inside(it) }) {
            (outward.tallies[link.origin] ?: inward.tallies.getValue(link.origin)).shown += 1
        }
        for (origin in outward.tallies.keys + inward.tallies.keys) {
            settle(listOfNotNull(outward.tallies[origin], inward.tallies[origin]))
        }
        return lost
    }

    private fun settle(rows: List<Tally>) {
        val left = rows.maxOf { it.served } - rows.sumOf { it.shown }
        if (left > 0) count(rows.mapNotNull { it.reason }.minOrNull() ?: Unseen.STOPPED, left)
    }

    private fun sift(reading: Reading, rows: Rows, derive: (FlowLink) -> Unseen?) {
        for (dropped in reading.dropped) {
            if (dropped.reason in readTwice) {
                rows.serve(dropped.origin).note(dropped.reason)
            } else {
                count(dropped.reason, 1)
            }
        }
        for (link in reading.links) {
            when {
                link.to == null -> Unit
                ends.inside(link) -> rows.serve(link.origin)
                else -> count(derive(link) ?: Unseen.STOPPED, 1)
            }
        }
    }

    private fun count(reason: Unseen, rows: Int) {
        lost.merge(reason, rows, Int::plus)
    }
}
