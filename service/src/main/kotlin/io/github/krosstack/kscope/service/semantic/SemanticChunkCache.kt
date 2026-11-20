package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat

/**
 * The chunk keys of each artifact, so that an artifact that did not change costs no round trip to the worker. It holds
 * metadata only, bound to the model identity; a file that is missing, damaged or of another identity reads as empty.
 */
internal class SemanticChunkCache(private val file: Path) {
    private var proof: Proof? = null

    data class Proof(
        val graphRevision: Long,
        val identity: ModelIdentity,
        val fingerprint: String,
        val artifacts: List<ArtifactKeys>?,
    )

    @Serializable
    data class Entry(val key: String, val kind: String, val nodes: List<NodeRef>)

    @Serializable
    data class ArtifactKeys(
        val artifact: ArtifactRef,
        val contentHash: String,
        val symbols: String,
        val chunks: List<Entry>,
    ) {
        fun toChunks(): List<Chunk> = chunks.map { Chunk(it.key, ChunkKind.valueOf(it.kind), "", it.nodes, artifact) }

        fun readable(): Boolean = chunks.all { entry -> ChunkKind.entries.any { it.name == entry.kind } }

        fun matches(artifact: Artifact, digest: String): Boolean =
            this.artifact == artifact.ref && contentHash == artifact.contentHash.value && symbols == digest

        companion object {
            fun of(artifact: ArtifactRef, contentHash: String, symbols: String, chunks: List<Chunk>) = ArtifactKeys(
                artifact,
                contentHash,
                symbols,
                chunks.map { Entry(it.key, it.kind.name, it.nodes) },
            )
        }
    }

    @Serializable
    private data class Record(val version: Int, val identity: ModelIdentity, val artifacts: List<ArtifactKeys>)

    fun read(identity: ModelIdentity): Map<ArtifactRef, ArtifactKeys> =
        record(identity)?.artifacts.orEmpty().filter { it.readable() }.associateBy { it.artifact }

    fun current(graph: GraphReader, identity: ModelIdentity, revision: Long): Proof? {
        val bytes = bytes() ?: return null
        val fingerprint = digest(bytes)
        return proof?.takeIf {
            it.graphRevision == revision && it.identity == identity && it.fingerprint == fingerprint
        } ?: run {
            val artifacts = record(identity, bytes)?.artifacts
            Proof(revision, identity, fingerprint, artifacts?.let { validate(graph, it) }).also { proof = it }
        }
    }

    fun fingerprint(): String? = bytes()?.let { digest(it) }

    private fun validate(graph: GraphReader, artifacts: List<ArtifactKeys>): List<ArtifactKeys>? {
        val cached = artifacts.associateBy { it.artifact }
        val current = graph.artifacts().sortedBy { KscopeJson.encodeToString(it.ref) }.map { artifact ->
            val symbols = graph.symbols(SymbolQuery(artifact = artifact.ref, limit = Int.MAX_VALUE))
            cached[artifact.ref]?.takeIf { entry ->
                entry.matches(artifact, symbolDigest(symbols)) && entry.readable() &&
                    entry.chunks.all { it.key.matches(Regex("[0-9a-f]{64}")) }
            }
        }
        return current.takeIf { entries ->
            cached.size == artifacts.size && entries.all { it != null }
        }?.filterNotNull()
    }

    private fun bytes(): ByteArray? = try {
        Files.readAllBytes(file)
    } catch (_: IOException) {
        null
    }

    private fun record(identity: ModelIdentity, bytes: ByteArray? = bytes()): Record? = try {
        bytes?.let {
            val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(it)).toString()
            KscopeJson.decodeFromString<Record>(text)
        }?.takeIf { it.version == VERSION && it.identity == identity }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    fun write(identity: ModelIdentity, artifacts: Collection<ArtifactKeys>) {
        val ordered = artifacts.sortedBy { KscopeJson.encodeToString(it.artifact) }
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, "${file.fileName}-", ".tmp")
        try {
            Files.writeString(temporary, KscopeJson.encodeToString(Record(VERSION, identity, ordered)))
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    companion object {
        private const val VERSION = 1

        fun symbolDigest(symbols: List<Symbol>): String =
            digest(symbols.map { KscopeJson.encodeToString(it) }.sorted().joinToString("\n"))

        private fun digest(text: String): String = digest(text.toByteArray())

        private fun digest(bytes: ByteArray): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
