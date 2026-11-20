package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.semantic.SemanticIndexer
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkBuilder
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.service.readWorkspaceFiles
import io.github.krosstack.kscope.service.semantic.FakeSemanticComponents
import io.github.krosstack.kscope.service.semantic.RecordingEmbedding
import io.github.krosstack.kscope.service.semantic.SemanticChunkCache
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.SemanticTestWorkspace
import io.github.krosstack.kscope.service.semantic.SemanticWorkspace
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.assertTrue

internal class SemanticQueryFixture(root: Path, initialize: (Path) -> Unit = SemanticTestWorkspace::create) :
    AutoCloseable {
    val provider = RecordingEmbedding()
    private val scope = run {
        initialize(root)
        Files.write(root.resolve("image.png"), byteArrayOf(0))
        SemanticTestWorkspace.open(root, FakeSemanticComponents(root.resolve("scratch")), SemanticIndexingMode.OFF)
            .also { it.scan() }
    }
    val directory: Path = root.resolve(".kscope")
    var symbolQueries = 0
    val graph = object : GraphReader by scope.graph() {
        override fun symbols(query: SymbolQuery): List<Symbol> {
            symbolQueries++
            return scope.graph().symbols(query)
        }
    }
    val cache = SemanticChunkCache(directory.resolve("embeddings-chunks.json"))
    val store = GenerationStore(directory)
    var revision = 1L
    var component = "component-a"
    val workspace = SemanticWorkspace({ true }, { graph }, scope::readWorkspaceFiles, { revision }, { false })
    var generationOpens = 0
    val current = CurrentSemanticGeneration(workspace, directory, store, { provider ->
        generationOpens++
        store.tryOpen(provider)
    }) { component }
    private val chunks = ChunkBuilder(provider::cap).build(graph, scope.readWorkspaceFiles())
    val inventory = graph.artifacts().map { artifact ->
        val symbols = graph.symbols(SymbolQuery(artifact = artifact.ref, limit = Int.MAX_VALUE))
        val bytes = symbols.map { KscopeJson.encodeToString(it) }.sorted().joinToString("\n").toByteArray()
        val digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        SemanticChunkCache.ArtifactKeys.of(
            artifact.ref,
            artifact.contentHash.value,
            digest,
            chunks.filter { it.artifact == artifact.ref },
        )
    }
    val validationQueries = inventory.size

    init {
        cache.write(provider.identity, inventory)
        store.publish(generation(chunks))
        provider.capCalls = 0
        provider.embedded.clear()
        symbolQueries = 0
    }

    fun generation(chunks: List<Chunk>): GenerationFile {
        val session = SemanticIndexer().begin(provider, null)
        chunks.chunked(16).forEach(session::accept)
        return session.finish()
    }

    fun assertNoLeases() {
        val leases = directory.resolve("embeddings-leases")
        assertTrue(!Files.exists(leases) || Files.list(leases).use { it.findAny().isEmpty })
    }

    override fun close() {
        current.close()
        assertNoLeases()
        scope.close()
    }
}
