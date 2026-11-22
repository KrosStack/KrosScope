package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.SymbolId

internal enum class Verdict { ADMIT, DROP, HALT }

internal class AdmissionGate(
    private val level: FlowLevel,
    private val nodes: Int,
    private val distance: Map<SymbolId, Int>,
) {
    private val kinds = HashMap<SymbolId, String>()

    private val refused = HashMap<SymbolId, Unseen>()

    val nonCallable: Map<SymbolId, String> get() = kinds

    val refusals: Map<SymbolId, Unseen> get() = refused

    fun judge(candidate: SymbolId): Verdict = when {
        candidate in distance -> Verdict.ADMIT
        classified(candidate) -> Verdict.DROP
        distance.size >= nodes -> Verdict.HALT
        level.admits(candidate) -> Verdict.ADMIT
        classified(candidate) -> Verdict.DROP
        level.spent -> Verdict.HALT
        else -> refuse(candidate)
    }

    fun classified(candidate: SymbolId): Boolean {
        val kind = level.rejected(candidate) ?: return false
        kinds[candidate] = kind
        refuse(candidate)
        return true
    }

    private fun refuse(candidate: SymbolId): Verdict {
        refused[candidate] = level.refusalOf(candidate)
        return Verdict.DROP
    }
}
