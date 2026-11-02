package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.hashing.digestOf
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.SourceSetId

internal class SourceSetArtifactDigests(members: Map<SourceSetId, List<Artifact>>, api: ProducerApiDigests?) {
    val version: String? = api?.version
    private val content = members.mapValues { (_, artifacts) -> digestOfArtifacts(artifacts) }
    private val dependency = api?.let { digests ->
        members.mapValues { (_, artifacts) ->
            digestOf(artifacts.flatMap { listOf(it.ref.path.value, digests.digest(it)) })
        }
    } ?: content

    fun digest(member: SourceSetId, owner: SourceSetId): String =
        (if (member == owner) content else dependency)[member].orEmpty()

    private fun digestOfArtifacts(artifacts: List<Artifact>): String =
        digestOf(artifacts.flatMap { listOf(it.ref.path.value, it.contentHash.value) })
}
