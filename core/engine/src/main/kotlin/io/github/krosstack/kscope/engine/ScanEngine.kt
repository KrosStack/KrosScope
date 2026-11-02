package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.discovery.GitAccess
import io.github.krosstack.kscope.engine.discovery.WorkspaceRepositories
import io.github.krosstack.kscope.engine.incremental.ApiDigestCache
import io.github.krosstack.kscope.engine.incremental.StoreContent
import io.github.krosstack.kscope.engine.incremental.SyntheticSourceCache
import io.github.krosstack.kscope.engine.incremental.VerifyComparator
import io.github.krosstack.kscope.engine.pipeline.PipelineResult
import io.github.krosstack.kscope.engine.pipeline.ScanContext
import io.github.krosstack.kscope.engine.pipeline.ScanPipeline
import io.github.krosstack.kscope.engine.progress.ProgressListener
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.spi.CapabilityRegistry
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.store.IndexProbe
import io.github.krosstack.kscope.store.SqliteGraphStore
import io.github.krosstack.kscope.store.dataVersion
import java.nio.file.Files

/**
 * Builds and updates the index of one workspace. Every update compares the fingerprints of the units that should
 * exist with the stored ones and produces only the difference. An engine is not thread-safe. [progress] is told which
 * stage runs and how far it is; it changes nothing about the result.
 */
public class ScanEngine internal constructor(
    private val registryOf: () -> CapabilityRegistry,
    config: WorkspaceConfig,
    internal val gitExecutable: String,
    private val progress: ProgressListener = ProgressListener.NONE,
) : AutoCloseable {
    internal constructor(
        registry: CapabilityRegistry,
        config: WorkspaceConfig,
        gitExecutable: String,
        progress: ProgressListener = ProgressListener.NONE,
    ) : this({ registry }, config, gitExecutable, progress)

    public constructor(
        registry: CapabilityRegistry,
        config: WorkspaceConfig,
        progress: ProgressListener = ProgressListener.NONE,
    ) : this({ registry }, config, GitAccess.DEFAULT_EXECUTABLE, progress)

    /** Asks for the registry once per scan, so that a scan uses one registry from its start to its end. */
    public constructor(
        registryOf: () -> CapabilityRegistry,
        config: WorkspaceConfig,
        progress: ProgressListener = ProgressListener.NONE,
    ) : this(registryOf, config, GitAccess.DEFAULT_EXECUTABLE, progress)

    internal val registry: CapabilityRegistry get() = registryOf()

    internal val config = config.copy(
        root = config.root.toAbsolutePath().normalize(),
        indexDirectory = config.indexDirectory.toAbsolutePath().normalize(),
    )
    private val files = IndexFiles(this.config.indexDirectory)
    private var openStore: SqliteGraphStore? = null
    private val carriedNotices = mutableListOf<Diagnostic>()

    /** A full scan when no index exists. With [verify], the result is compared with a clean rebuild. */
    public fun update(verify: Boolean = false): UpdateReport = runUpdate(verify)

    /** Drops the index and scans from scratch. Readers obtained from [graph] before the call are invalid. */
    public fun rebuild(verify: Boolean = false): UpdateReport {
        close()
        files.deleteDerivedData()
        return runUpdate(verify)
    }

    /** Whether the index file holds a current index with files in it; looking does not create or change anything. */
    public fun hasIndexedFiles(): Boolean = IndexProbe.hasArtifacts(files.database)

    /** Valid until the next [rebuild] or [close]. */
    public fun graph(): GraphReader = store()

    /** How many structure entries and diagnostics the index holds; the database counts them. */
    public fun indexCounts(): IndexCounts = store().counts().let {
        IndexCounts(it.repositories, it.buildRoots, it.modules, it.sourceSets, it.targets, it.variants, it.diagnostics)
    }

    /** Ends the read snapshot and returns the version of external commits on this connection. */
    public fun dataVersion(): Long = store().dataVersion()

    /** The lexical index of the symbols and artifacts of [graph]; valid as long as the reader is. */
    public fun searchIndex(): SearchIndex = store().searchIndex()

    override fun close() {
        openStore?.close()
        openStore = null
    }

    private fun runUpdate(verify: Boolean): UpdateReport {
        val store = store()
        store.dataVersion()
        val fixed = config.withSettingsFixed()
        val layout = WorkspaceRepositories(
            fixed.root,
            fixed.indexDirectory,
            executable = gitExecutable,
        ).resolve(fixed.registered())
        val context = ScanContext(registry, fixed, layout, progress)
        val result = ScanPipeline(
            context,
            store,
            SyntheticSourceCache(files.syntheticSources),
            ApiDigestCache(files.apiDigests),
        ).run()
        fixed.checkoutsAccepted(layout.registeredCheckouts())
        val verification = if (verify) VerifyRun(context).compareWith(store) else null
        return reportOf(result, verification, store)
    }

    private fun reportOf(result: PipelineResult, verification: VerifyReport?, store: GraphStore): UpdateReport {
        val carried = carriedNotices.toList().also { carriedNotices.clear() }
        val diagnostics = (store.diagnostics() + carried + result.notices).distinct().sortedWith(diagnosticOrder)
        return UpdateReport(
            unitsProduced = result.unitsProduced,
            unitsSkipped = result.unitsSkipped,
            unitsRemoved = result.unitsRemoved,
            artifacts = result.artifacts,
            diagnostics = diagnostics,
            verify = verification,
            stageMillis = result.stageMillis,
        )
    }

    private fun store(): SqliteGraphStore {
        openStore?.let { return it }
        val opened = IndexStore.open(files)
        carriedNotices += opened.notices
        openStore = opened.store
        return opened.store
    }

    private companion object {
        val diagnosticOrder = compareByDescending<Diagnostic> { it.severity }
            .thenBy { it.code.value }
            .thenBy { it.message }
    }
}

/** A scan from scratch into a throwaway store, to check an incrementally updated store against. */
internal class VerifyRun(private val context: ScanContext) {
    fun compareWith(incremental: GraphStore): VerifyReport {
        val directory = Files.createTempDirectory("kscope-verify")
        try {
            val rebuilt = IndexStore.open(IndexFiles(directory)).store.use { fresh ->
                ScanPipeline(context.forVerification(), fresh, SyntheticSourceCache(null), ApiDigestCache(null)).run()
                StoreContent(fresh.batches(), fresh.searchDocuments())
            }
            return VerifyComparator.compare(StoreContent(incremental.batches(), incremental.searchDocuments()), rebuilt)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
