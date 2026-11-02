package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.spi.ApiDigester
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.WorkspaceFiles

internal class ProducerApiDigests(val version: String, private val digests: Map<ArtifactRef, String>) {
    fun digest(artifact: Artifact): String = digests[artifact.ref] ?: artifact.contentHash.value
}

/** Collects API digests once per producer for the source artifacts whose contents are not cached. */
internal class ApiDigestStage(
    private val cache: ApiDigestCache,
    private val artifacts: List<Artifact>,
    private val files: WorkspaceFiles,
) {
    fun run(producers: List<FactProducer>): Map<String, ProducerApiDigests> {
        val digests = producers.mapNotNull { producer ->
            if (producer.descriptor.granularity != OwnershipKind.SOURCE_SET) return@mapNotNull null
            val digester = producer.apiDigester ?: return@mapNotNull null
            producer.descriptor.producer.id to digest(producer.descriptor.producer.id, digester)
        }.toMap()
        cache.save()
        return digests
    }

    private fun digest(producer: String, digester: ApiDigester): ProducerApiDigests {
        val types = digester.consumes.map { it.id }.toSet()
        val candidates = artifacts.filter { it.sourceSet != null && it.type.id in types }.sortedBy { it.ref.path.value }
        val missing = candidates.filter { cache.lookup(producer, digester.version, it) == null }
        if (missing.isNotEmpty()) {
            val answers = digester.digest(missing, files)
            missing.forEach { artifact ->
                val digest = answers.digests[artifact.ref]
                if (digest != null || artifact.ref in answers.withoutDigest) {
                    cache.remember(producer, digester.version, artifact, digest)
                }
            }
        }
        val digests = candidates.mapNotNull { artifact ->
            cache.lookup(producer, digester.version, artifact)?.digest?.let { artifact.ref to it }
        }.toMap()
        return ProducerApiDigests(digester.version, digests)
    }
}
