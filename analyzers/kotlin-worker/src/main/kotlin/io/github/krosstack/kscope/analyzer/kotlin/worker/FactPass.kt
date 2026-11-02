package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisSessions
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.DeclarationPass
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.SessionGroups
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.IdentityCollisions
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.RelationshipPass
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.worker.WorkerModule

/**
 * Everything the worker derives from an analysis session: one batch per requested source set, holding the symbols
 * and diagnostics of the declaration pass followed by the relationships of the resolution pass. [batchSequence]
 * derives a batch when it is asked for, so that a caller that sends each batch on holds one source set at a time.
 */
internal class FactPass(private val producer: ProducerRef) {
    private val declarations = DeclarationPass(producer)
    private val relationships = RelationshipPass()

    fun batchesOf(workspace: AnalysisWorkspace): List<FactBatch> = batchSequence(workspace).toList()

    /** The batches of a request: each session that [SessionGroups] forms is opened and closed in turn. */
    fun batchSequence(modules: List<WorkerModule>): Sequence<FactBatch> = sequence {
        SessionGroups(modules).sessions().forEach { session ->
            AnalysisSessions.open(session).use { yieldAll(batchSequence(it)) }
        }
    }

    fun batchSequence(workspace: AnalysisWorkspace): Sequence<FactBatch> {
        val collisions = IdentityCollisions { declarations.collidedIdsOf(workspace.sourceSets) }
        return workspace.sourceSets.asSequence().filter { it.source.emitFactsFor }.map { sourceSet ->
            FactBatch(
                producer,
                OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.source.sourceSet.value),
                sourceSet.source.fingerprint,
                declarations.factsOf(workspace, sourceSet) + relationships.factsOf(workspace, sourceSet, collisions),
            )
        }
    }
}
