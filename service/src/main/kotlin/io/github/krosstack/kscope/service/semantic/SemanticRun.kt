package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.semantic.SemanticIndexer
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkBuilder
import io.github.krosstack.kscope.semantic.chunk.ChunkCursor
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.semantic.store.PreparedGeneration
import io.github.krosstack.kscope.semantic.store.PublishResult
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import java.nio.file.Path

/**
 * One indexing job on the owner thread of the workspace: it reads the graph, which is not thread safe, and moves by
 * one bounded step per call, so a caller can interleave queries between steps. First every artifact yields its chunk
 * keys, which fixes how far the published index is behind; then artifacts with missing vectors are embedded in
 * batches of at most 16 texts; last the generation is published only if nothing changed meanwhile.
 */
internal class SemanticRun(
    val revision: Long,
    private val ready: SemanticComponents.Opening.Ready,
    private val workspace: SemanticWorkspace,
    directory: Path,
    private val now: () -> Long,
) : AutoCloseable {
    enum class Phase { PREPARING, EMBEDDING, PUBLISHING }

    enum class Outcome { WORKING, WAITING, PUBLISHED, SUPERSEDED }

    val provider = ready.provider
    var phase = Phase.PREPARING
        private set
    var behind = 0
        private set
    var deferredReason: String? = null
        private set

    val status: SemanticStatus
        get() = when {
            phase == Phase.PREPARING -> SemanticStatus(SemanticState.WORKER_UNAVAILABLE, provider.id, provider.identity)
            behind == 0 -> SemanticStatus(SemanticState.USED, provider.id, provider.identity)
            else -> SemanticStatus(SemanticState.BEHIND, provider.id, provider.identity, behind)
        }

    val notice: String?
        get() = when {
            phase == Phase.PREPARING -> "Semantic index is preparing."

            deferredReason != null ->
                "Semantic index is behind by $behind chunks; publication is deferred: $deferredReason."

            behind > 0 -> "Semantic index is behind by $behind chunks."

            else -> null
        }

    private val store = GenerationStore(directory)
    private val guard = SemanticScanGuard(directory)
    private val cache = SemanticChunkCache(directory.resolve(CACHE_FILE))
    private val builder = ChunkBuilder(provider::cap)
    private val files = workspace.files()
    private val cached = cache.read(provider.identity)
    private val artifacts = ArrayDeque(
        workspace.graph().artifacts().sortedBy { KscopeJson.encodeToString(it.ref) },
    )
    private val expected = store.current(provider.id)

    // The lease is opened last, so nothing above can fail while it is held.
    private val lease = store.open(provider.id)
    private val session = SemanticIndexer().begin(provider, lease?.generation)
    private val storedKeys = lease?.generation?.takeIf { it.header.identity == provider.identity }
        ?.chunks.orEmpty().map { it.chunk.key }.toSet()
    private val currentKeys = mutableSetOf<String>()
    private val records = mutableListOf<SemanticChunkCache.ArtifactKeys>()
    private val missing = ArrayDeque<Artifact>()
    private var cursor: ChunkCursor? = null
    private var prepared: PreparedGeneration? = null
    private var retryAt = 0L

    fun lostReservation(): String? = ready.lost()

    fun step(): Outcome = when (phase) {
        Phase.PREPARING -> Outcome.WORKING.also { prepareNext() }
        Phase.EMBEDDING -> Outcome.WORKING.also { embedNext() }
        Phase.PUBLISHING -> publish()
    }

    private fun prepareNext() {
        val artifact = artifacts.removeFirstOrNull()
        if (artifact == null) {
            behind = (currentKeys - storedKeys).size
            cache.write(provider.identity, records)
            phase = if (missing.isEmpty()) Phase.PUBLISHING else Phase.EMBEDDING
            return
        }
        val symbols = symbolsOf(artifact)
        val digest = SemanticChunkCache.symbolDigest(symbols)
        val known = cached[artifact.ref]?.takeIf { it.matches(artifact, digest) }
        val keys = known ?: SemanticChunkCache.ArtifactKeys.of(
            artifact.ref,
            artifact.contentHash.value,
            digest,
            keysOf(cursorOf(artifact, symbols)),
        )
        records += keys
        currentKeys += keys.chunks.map { it.key }
        if (keys.chunks.all { it.key in storedKeys }) {
            keys.toChunks().chunked(MAX_BATCH_CHUNKS).forEach(session::accept)
        } else {
            missing += artifact
        }
    }

    private fun keysOf(cursor: ChunkCursor?): List<Chunk> = buildList {
        while (cursor != null && !cursor.complete) {
            addAll(cursor.next(MAX_BATCH_CHUNKS, MAX_BATCH_BYTES).map { it.copy(text = "") })
        }
    }

    private fun symbolsOf(artifact: Artifact) =
        workspace.graph().symbols(SymbolQuery(artifact = artifact.ref, limit = Int.MAX_VALUE))

    private fun cursorOf(artifact: Artifact, symbols: List<Symbol>): ChunkCursor? =
        when (val source = SemanticSources.read(artifact, files)) {
            is SemanticSources.Read.Content -> builder.cursor(artifact, symbols, source.text)

            SemanticSources.Read.Excluded -> null

            SemanticSources.Read.Stale, SemanticSources.Read.Unreadable ->
                error("${artifact.ref.path.value} no longer matches the scan")
        }

    private fun embedNext() {
        val open = cursor ?: missing.removeFirstOrNull()?.let { cursorOf(it, symbolsOf(it)) }?.also { cursor = it }
        if (open == null) {
            if (missing.isEmpty()) phase = Phase.PUBLISHING
            return
        }
        session.accept(open.next(MAX_BATCH_CHUNKS, MAX_BATCH_BYTES))
        if (open.complete) cursor = null
    }

    private fun publish(): Outcome {
        val generation = prepared ?: store.prepare(session.finish()).also {
            prepared = it
        }
        if (now() < retryAt) return Outcome.WAITING
        val result = guard.exclusively {
            workspace.refresh()
            if (workspace.revision() != revision || store.current(provider.id) != expected) {
                null
            } else {
                store.publish(generation, expected)
            }
        }
        return when (result) {
            null -> Outcome.SUPERSEDED

            is PublishResult.Published -> Outcome.PUBLISHED

            is PublishResult.Deferred -> {
                deferredReason = result.reason
                retryAt = now() + RETRY_MILLIS
                Outcome.WAITING
            }
        }
    }

    override fun close() {
        try {
            prepared?.close()
        } finally {
            lease?.close()
        }
    }

    companion object {
        const val CACHE_FILE = "embeddings-chunks.json"
        private const val MAX_BATCH_CHUNKS = 16
        private const val MAX_BATCH_BYTES = 256 * 1024
        private const val RETRY_MILLIS = 1_000L
    }
}
