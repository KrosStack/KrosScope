package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.semantic.SemanticIndexer
import io.github.krosstack.kscope.semantic.search.SemanticFileRanking
import io.github.krosstack.kscope.semantic.store.GenerationCurrent
import io.github.krosstack.kscope.semantic.store.GenerationLease
import io.github.krosstack.kscope.semantic.store.GenerationOpening
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.service.semantic.SemanticChunkCache
import io.github.krosstack.kscope.service.semantic.SemanticRun
import io.github.krosstack.kscope.service.semantic.SemanticWorkspace
import io.github.krosstack.kscope.spi.embedding.EmbeddingProvider
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import java.io.IOException
import java.nio.file.Path

internal class CurrentSemanticGeneration(
    private val workspace: SemanticWorkspace,
    directory: Path,
    private val store: GenerationStore = GenerationStore(directory),
    private val openGeneration: (String) -> GenerationOpening = store::tryOpen,
    private val componentFingerprint: () -> String,
) : AutoCloseable {
    private val cache = SemanticChunkCache(directory.resolve(SemanticRun.CACHE_FILE))
    private var snapshot: Snapshot? = null
    private var closed = false

    data class Snapshot(
        val identity: Identity,
        val entries: List<SemanticFileRanking.Entry>,
        val behindChunks: Int,
        val lease: GenerationLease,
    )

    data class Identity(
        val graphRevision: Long,
        val generationName: String,
        val provider: String,
        val modelIdentity: ModelIdentity,
        val inventoryFingerprint: String,
        val componentFingerprint: String,
    )

    sealed interface Opening {
        data class Ready(val snapshot: Snapshot) : Opening
        data class Unproven(val reason: String) : Opening
        data class Unavailable(val opening: GenerationOpening) : Opening
    }

    fun open(provider: EmbeddingProvider): Opening {
        if (closed) return unavailable("Admission is closed")
        val opening = admit(provider)
        if (opening !is Opening.Ready) invalidate()
        snapshot = (opening as? Opening.Ready)?.snapshot
        return opening
    }

    private fun admit(provider: EmbeddingProvider): Opening = try {
        workspace.refresh()
        val previous = snapshot
        if (!workspace.indexed()) {
            Opening.Unproven("Graph identity is unknown")
        } else if (previous != null && isCurrent(previous, provider)) {
            Opening.Ready(previous)
        } else {
            invalidate()
            when (val opening = openGeneration(provider.id)) {
                is GenerationOpening.Opened -> prove(provider, opening)
                else -> Opening.Unavailable(opening)
            }
        }
    } catch (_: IOException) {
        Opening.Unproven("Current inventory is unreadable")
    } catch (_: IllegalArgumentException) {
        Opening.Unproven("Current inventory is invalid")
    } catch (_: IllegalStateException) {
        unavailable("Semantic identity changed")
    }

    private fun prove(provider: EmbeddingProvider, opened: GenerationOpening.Opened): Opening {
        var retained = false
        try {
            val generation = opened.lease.generation
            val identity = Identity(
                workspace.revision(),
                opened.name,
                provider.id,
                provider.identity,
                "",
                componentFingerprint(),
            )
            val opening = when {
                generation.header.provider != identity.provider ||
                    generation.header.identity != identity.modelIdentity ->
                    unavailable("Incompatible generation identity")

                identity.componentFingerprint.isBlank() -> Opening.Unproven("Component identity is unknown")

                else -> prepare(provider, opened, identity)
            }
            retained = opening is Opening.Ready
            return opening
        } finally {
            if (!retained) opened.lease.close()
        }
    }

    private fun prepare(provider: EmbeddingProvider, opened: GenerationOpening.Opened, identity: Identity): Opening {
        val proof = cache.current(workspace.graph(), identity.modelIdentity, identity.graphRevision)
        val inventory = proof?.artifacts
            ?: return Opening.Unproven("Current inventory is incomplete")
        val generation = opened.lease.generation
        val storedKeys = generation.chunks.map { it.chunk.key }.toSet()
        val chunks = inventory.flatMap { it.toChunks() }
        val owners = chunks.groupBy { it.key }.mapValues { (_, shared) -> shared.map { it.artifact } }
        val session = SemanticIndexer().begin(provider, generation)
        chunks.filter { it.key in storedKeys }.forEach { session.accept(listOf(it)) }
        val entries = session.finish().chunks.map {
            val artifacts = it.chunk.nodes.filterIsInstance<NodeRef.OfArtifact>().map { node -> node.ref }
            SemanticFileRanking.Entry(it.vector, (owners.getValue(it.chunk.key) + artifacts).toSet())
        }
        val candidate = Snapshot(
            identity.copy(inventoryFingerprint = proof.fingerprint),
            entries,
            (chunks.map { it.key }.toSet() - storedKeys).size,
            opened.lease,
        )
        return when {
            entries.isEmpty() -> unavailable("No current vectors")
            !isCurrent(candidate, provider) -> unavailable("Semantic snapshot superseded")
            else -> Opening.Ready(candidate)
        }
    }

    private fun isCurrent(candidate: Snapshot, provider: EmbeddingProvider): Boolean {
        val component = componentFingerprint()
        val fingerprint = cache.fingerprint() ?: return false
        workspace.refresh()
        val observed = Identity(
            workspace.revision(),
            candidate.identity.generationName,
            provider.id,
            provider.identity,
            fingerprint,
            component,
        )
        return workspace.indexed() && observed == candidate.identity &&
            when (val selected = store.tryCurrent(provider.id)) {
                is GenerationCurrent.Current -> selected.name == candidate.identity.generationName
                else -> false
            }
    }

    fun invalidate() {
        snapshot?.lease?.close()
        snapshot = null
    }

    override fun close() {
        invalidate()
        closed = true
    }

    private fun unavailable(reason: String) = Opening.Unavailable(GenerationOpening.Unavailable(reason))
}
