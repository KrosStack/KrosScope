package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.SymbolId

internal class FlowWalk(private val level: FlowLevel, private val limits: WalkLimits, selected: SymbolId) {
    private val distance = linkedMapOf(selected to 0)
    private val leaving = HashMap<SymbolId, Reading>()
    private val entering = HashMap<SymbolId, Reading>()
    private val coverage = Coverage(selected)
    private val gate = AdmissionGate(level, limits.nodes, distance)
    private var pendingDownstream = setOf(selected)
    private var pendingUpstream = setOf(selected)
    private val nextDownstream = mutableListOf<SymbolId>()
    private val nextUpstream = mutableListOf<SymbolId>()
    private var current = 0
    private var halted = false

    private val discovering: Boolean get() = limits.depth <= 0 || current < limits.depth

    private val Reading.cutShort: Boolean get() = !complete && level.spent

    fun walk(): FlowGraph {
        while (!halted && (pendingDownstream.isNotEmpty() || pendingUpstream.isNotEmpty())) advance()
        val links = WalkLinks(distance, leaving, entering)
        val drawn = links.drawn()
        val undrawn = links.undrawn(coverage.followed, gate.nonCallable)
        val ends = Ends(distance, coverage.followed, gate.refusals)
        val unseen = Accounting(ends, leaving, entering).unseen(drawn)
        return FlowGraph(distance, drawn, coverage.unread(distance.keys), undrawn, unseen)
    }

    private fun advance() {
        nextDownstream.clear()
        nextUpstream.clear()
        val units = (pendingDownstream + pendingUpstream).sortedBy { it.value }
        units.forEachIndexed { index, unit ->
            if (!halted) visit(unit, units.size - index - 1)
        }
        pendingDownstream = nextDownstream.toSet()
        pendingUpstream = nextUpstream.toSet()
        current += 1
    }

    private fun visit(unit: SymbolId, waiting: Int) {
        val share = Share(if (discovering && unit in pendingUpstream) 2 else 1, waiting)
        val reading = leaving.getOrPut(unit) { level.outgoing(unit, share) }
        when {
            discovering -> expand(unit, reading, waiting)

            reading.complete -> {
                coverage.downstreamRead += unit
                coverage.upstreamRead += unit
            }
        }
        if (reading.cutShort) halted = true
    }

    private fun expand(unit: SymbolId, reading: Reading, waiting: Int) {
        val down = unit in pendingDownstream
        val targets = if (down) reading.links.mapNotNull { it.to }.filter { it !in coverage.seenDownstream } else null
        val following = Share(1, waiting + reserve(targets.orEmpty().toSet()))
        val incoming = if (unit in pendingUpstream && !reading.cutShort) level.incoming(unit, following) else null
        incoming?.let { entering[unit] = it }
        val sources = incoming?.links?.map { it.from }?.filter { it !in coverage.seenUpstream }
        widen(targets.orEmpty().toSet(), sources.orEmpty().toSet())
        if (down) {
            coverage.followed += unit
            if (reading.complete) coverage.downstreamRead += unit
        }
        if (incoming?.complete == true) coverage.upstreamRead += unit
        if (incoming?.cutShort == true) halted = true
    }

    private fun reserve(targets: Set<SymbolId>): Int {
        val room = limits.nodes - distance.size
        val fresh = targets.count { it !in distance }
        return if (fresh >= room) 0 else fresh
    }

    private fun widen(down: Set<SymbolId>, up: Set<SymbolId>) {
        for (candidate in (down + up).sortedBy { it.value }) {
            val way = Way(candidate in down, candidate in up)
            if (halted) park(candidate, way) else admit(candidate, way)
        }
    }

    private fun admit(candidate: SymbolId, way: Way) {
        when (gate.judge(candidate)) {
            Verdict.ADMIT -> {
                distance.getOrPut(candidate) { current + 1 }
                coverage.see(candidate, way)
                if (way.downstream) nextDownstream += candidate
                if (way.upstream) nextUpstream += candidate
            }

            Verdict.HALT -> {
                halted = true
                coverage.skipped += candidate
            }

            Verdict.DROP -> Unit
        }
    }

    private fun park(candidate: SymbolId, way: Way) {
        when {
            candidate in distance -> coverage.see(candidate, way)
            !gate.classified(candidate) -> coverage.skipped += candidate
        }
    }
}
