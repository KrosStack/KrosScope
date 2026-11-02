package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Repository

/** The inventory of the repository: its facts, every artifact with its source set, and the oversized-file notices. */
internal object DiscoveryUnit {
    val producer = ProducerRef("kscope.discovery", "1")

    fun plan(repository: Repository, artifacts: List<Artifact>, maxArtifactBytes: Long): FactBatch {
        val owner = OwnershipUnit(OwnershipKind.REPOSITORY, repository.id.value)
        val oversized = artifacts.filter { it.sizeBytes > maxArtifactBytes }
        val facts = listOf<Fact>(Fact.OfRepository(repository)) +
            artifacts.map { Fact.OfArtifact(it) } +
            oversized.map { Fact.OfDiagnostic(tooLarge(it, maxArtifactBytes)) }
        return FactBatch(producer, owner, factFingerprint(producer, facts), facts)
    }

    private fun tooLarge(artifact: Artifact, limit: Long) = Diagnostic(
        severity = DiagnosticSeverity.INFO,
        code = ReasonCode("discovery.artifact-too-large"),
        message = "${artifact.ref.path.value} has ${artifact.sizeBytes} bytes, above the limit of $limit; " +
            "it is inventoried but not analysed.",
        location = null,
    )
}

/** A fingerprint of everything a unit contains, for units whose content is their input. */
internal fun factFingerprint(producer: ProducerRef, facts: List<Fact>): Fingerprint = FingerprintBuilder()
    .add(producer.id)
    .add(producer.version)
    .addAll(facts.map { KscopeJson.encodeToString(Fact.serializer(), it) }.sorted())
    .build()
