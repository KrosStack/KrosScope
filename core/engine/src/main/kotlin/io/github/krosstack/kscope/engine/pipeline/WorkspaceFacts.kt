package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef

/**
 * Stores what a producer found across the repositories under the one ownership unit of the workspace. The
 * fingerprint covers the facts and, when the producer read files outside the workspace, the [inputs] that describe
 * them.
 */
internal class WorkspaceFacts(private val session: UpdateSession) {
    fun store(producer: ProducerRef, facts: List<Fact>, inputs: List<String> = emptyList()) {
        val builder = FingerprintBuilder()
            .add(producer.id)
            .add(producer.version)
            .addAll(facts.map { KscopeJson.encodeToString(Fact.serializer(), it) })
        if (inputs.isNotEmpty()) builder.add(INPUTS).addAll(inputs)
        val fingerprint = builder.build()
        val unit = PlannedUnit(producer, OwnershipUnit(OwnershipKind.WORKSPACE, WORKSPACE_KEY), fingerprint)
        if (session.ledger.claimIfStale(unit)) {
            session.ledger.commit(FactBatch(unit.producer, unit.owner, unit.fingerprint, facts))
        }
    }

    private companion object {
        const val WORKSPACE_KEY = "workspace"
        const val INPUTS = "inputs"
    }
}
