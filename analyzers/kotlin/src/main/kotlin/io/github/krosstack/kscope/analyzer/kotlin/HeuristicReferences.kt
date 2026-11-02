package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisPlan
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolEcosystem

/**
 * Lowers what the compiler resolved to heuristic where the build model does not vouch for the module it resolved into.
 * A reference whose target is declared in a module that the module of the reference reaches only through heuristic
 * dependencies, such as a library whose source is built at another version than the one pinned, is as uncertain as
 * those dependencies. Its sub-kind and evidence class stay.
 */
internal object HeuristicReferences {
    fun applyTo(batch: FactBatch, plan: AnalysisPlan): FactBatch =
        batch.copy(facts = batch.facts.map { fact -> lowered(fact, plan) })

    private fun lowered(fact: Fact, plan: AnalysisPlan): Fact =
        if (fact is Fact.OfRelationship) Fact.OfRelationship(lowered(fact.value, plan)) else fact

    private fun lowered(relationship: Relationship, plan: AnalysisPlan): Relationship =
        when (val resolution = relationship.resolution) {
            is Resolution.Resolved ->
                if (resolution.certainty != Certainty.HEURISTIC && reachesOnlyHeuristically(relationship, plan)) {
                    relationship.copy(resolution = resolution.copy(certainty = Certainty.HEURISTIC))
                } else {
                    relationship
                }

            is Resolution.Unresolved, is Resolution.NotAnalysed -> relationship
        }

    private fun reachesOnlyHeuristically(relationship: Relationship, plan: AnalysisPlan): Boolean {
        val from = relationship.scope.sourceSet
        val to = ((relationship.resolution as? Resolution.Resolved)?.target)?.let(::workspaceModuleOf)
        return from != null && to != null && plan.reach.reachesOnlyHeuristically(from, to)
    }

    private fun workspaceModuleOf(target: NodeRef): ModuleId? {
        val parsed = (target as? NodeRef.OfSymbol)?.id?.identity() as? ParsedSymbolId.Valid
        return parsed?.identity?.takeIf { it.ecosystem == SymbolEcosystem.GRADLE }?.let { ModuleId(it.unit) }
    }
}
