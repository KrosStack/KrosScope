package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolEcosystem
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.assertEquals

/**
 * The integrity rule of the resolution pass: a resolved workspace target that lies in a source set whose facts the
 * request asked for is a symbol emitted in the same run, and so is the origin of every relationship.
 */
object Integrity {
    fun assertNoDanglingTargets(
        batches: List<FactBatch>,
        modules: List<WorkerModule>,
        synthesized: (SymbolId) -> Boolean = { false },
    ) {
        val emitted = batches.symbols().map { it.id }.toSet()
        val emitting = modules.flatMap { module -> module.sourceSets.filter { it.emitFactsFor } }
            .map { it.module.value to it.sourceSet.value.substringAfter("${it.module.value}@") }.toSet()
        val dangling = batches.relationships().flatMap { relationship ->
            val target = (relationship.resolution as? Resolution.Resolved)?.target
            listOfNotNull(relationship.from, target).mapNotNull { (it as? NodeRef.OfSymbol)?.id }
                .filter { isEmittingWorkspaceSymbol(it, emitting) && it !in emitted && !synthesized(it) }
        }
        assertEquals(emptyList(), dangling.distinct().map { it.value }, "Relationships point at missing symbols")
    }

    private fun isEmittingWorkspaceSymbol(id: SymbolId, emitting: Set<Pair<String, String>>): Boolean {
        val identity = (id.identity() as? ParsedSymbolId.Valid)?.identity ?: return false
        return identity.ecosystem == SymbolEcosystem.GRADLE && (identity.unit to identity.sourceSet) in emitting
    }
}
