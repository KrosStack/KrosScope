package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.indexLevel
import io.github.krosstack.kscope.engine.scope.isIndexedWith
import io.github.krosstack.kscope.engine.scope.isIndexedWithout
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.GraphQueries
import io.github.krosstack.kscope.retrieval.GraphResult
import io.github.krosstack.kscope.retrieval.GraphScope
import io.github.krosstack.kscope.retrieval.ImpactResult
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RelatedResult
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SymbolResult
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.graph.ImpactClosure
import io.github.krosstack.kscope.service.graph.repositoryEntries
import io.github.krosstack.kscope.service.level.LevelInForce
import io.github.krosstack.kscope.service.registry.ExternalCheckouts
import io.github.krosstack.kscope.service.registry.RepositoryRegistry
import io.github.krosstack.kscope.service.registry.ScannedCheckouts
import io.github.krosstack.kscope.service.settings.FreshnessReading
import io.github.krosstack.kscope.service.settings.IndexedScope
import io.github.krosstack.kscope.service.settings.SettingsFile
import io.github.krosstack.kscope.service.settings.SettingsReport
import io.github.krosstack.kscope.service.settings.SettingsSource
import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import io.github.krosstack.kscope.service.trust.TrustStore
import io.github.krosstack.kscope.service.wiring.DefaultCapabilities
import io.github.krosstack.kscope.service.wiring.SelectedRegistry
import io.github.krosstack.kscope.service.wiring.TokenCountFile
import io.github.krosstack.kscope.service.wiring.ToolVersion
import io.github.krosstack.kscope.service.wiring.WorkspaceLocation
import io.github.krosstack.kscope.service.wiring.toEngine
import io.github.krosstack.kscope.service.wiring.toEntry
import io.github.krosstack.kscope.service.wiring.toScanResult
import io.github.krosstack.kscope.service.wiring.toSummary
import io.github.krosstack.kscope.service.wiring.withNotice
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.search.SearchIndex
import java.nio.file.Path

/**
 * [trustBuild] asks for level [ScanLevel.EXACT], which executes the build logic of the workspace; so does [level] when
 * it is [ScanLevel.EXACT]. Either is the user's own request for that scan. Without one, a scan keeps the level of the
 * index and deepens to what `kscope.toml` asks for, and runs the build only in a workspace that the user trusted with
 * [TrustStore]. [progress] hears which step of a scan or query runs and how far it is; it never changes a result.
 * [tokenCache] keeps the token counts of `analyze` and `context` in the index directory between runs; it never
 * changes a result either. [settings] are the settings of the workspace
 * for a caller that must not depend on a file inside it, such as a benchmark, which passes
 * [WorkspaceSettings.DEFAULTS]; when null, a scan and `info` read `kscope.toml` in the workspace root, and a query
 * reads nothing.
 */
public data class OpenOptions(
    public val trustBuild: Boolean = false,
    public val indexDirectory: Path? = null,
    public val progress: ProgressListener = ProgressListener.NONE,
    public val tokenCache: Boolean = true,
    public val settings: WorkspaceSettings? = null,
    public val level: ScanLevel? = null,
)

/** The use-case API of KrosScope for one workspace. Frontends render its serializable results. */
public class KrosScope private constructor(
    internal val engine: ScanEngine,
    location: WorkspaceLocation,
    internal val progress: ProgressListener,
    internal val tokenCounts: TokenCountFile?,
    private val levels: LevelInForce,
) : AutoCloseable {
    private val settings: SettingsSource = levels.settings
    internal val workspaceRoot: Path = location.root
    internal val externalCheckouts: ExternalCheckouts = location.externalCheckouts

    /** The level that the settings of the workspace ask for, or null when they say nothing. */
    internal val levelAskedBySettings: ScanLevel? get() = settings.requestedLevel()

    /** Whether the user stored trust for this workspace on this machine; read from the file on every call. */
    internal val workspaceTrusted: Boolean get() = settings.isTrusted()
    private val indexFile = location.indexDirectory.resolve(INDEX_FILE_NAME)
    private val indexLocation = location.indexDirectory.toString().replace('\\', '/')
    internal val memory = IndexMemory(engine, progress, indexFile, indexLocation)

    /**
     * Builds the index, or updates it incrementally. A rebuild discards the existing index first. A settings file
     * that is not valid fails the scan with [io.github.krosstack.kscope.service.settings.InvalidSettingsException]
     * before anything is changed. A level that is not allowed fails it with [LevelRefusedException] in the same way.
     */
    public fun scan(rebuild: Boolean = false, verify: Boolean = false): ScanResult {
        settings.prepareScan()
        val run = levels.decide({ engine.indexLevel()?.let(ScanLevel::of) }, rebuild)
        levels.fix(run.level)
        memory.forget()
        if (rebuild) tokenCounts?.discard()
        val report = if (rebuild) engine.rebuild(verify) else engine.update(verify)
        memory.forget()
        tokenCounts?.prune(engine.graph().artifacts().map { it.contentHash }.toSet())
        val result = report.toScanResult(indexLocation, memory.levelMemory.level() ?: run.level)
        return run.notice?.let(result::withNotice) ?: result
    }

    /**
     * Whether the workspace has a current index with files in it. Asking opens nothing and creates nothing, so a
     * workspace that was never scanned stays as it is.
     */
    public fun isIndexed(): Boolean = engine.hasIndexedFiles()

    /**
     * Summarizes the index. A workspace that was never scanned yields an empty summary with `indexed` false. The
     * settings are read from their file each time, and a file that is not valid is reported, not raised.
     */
    public fun info(): InfoResult {
        val graph = memory.storeOrNull()
            ?: return emptyInfo(indexLocation, settings.report(IndexedScope.NOT_INDEXED))
        val coverage = graph.coverage()
        val counts = engine.indexCounts()
        return InfoResult(
            toolVersion = ToolVersion.value,
            indexLocation = indexLocation,
            indexed = true,
            structure = counts.toSummary(),
            artifactsByType = coverage.artifactsByType.toSortedMap(),
            symbolsByKind = coverage.symbolsByKind.toSortedMap(),
            coverage = coverage.cells.map { it.toEntry() },
            diagnostics = counts.diagnostics,
            metricsByName = coverage.metricsByName.toSortedMap(),
            searchIndexPopulated = coverage.searchDocuments.values.sum() > 0,
            settings = settings.report(IndexedScope(true, engine::isIndexedWith, engine::isIndexedWithout)),
            repositories = graph.structure().repositoryEntries(),
            indexLevel = memory.levelMemory.level()?.wireName,
        )
    }

    /**
     * Read access to the persisted graph for clients that measure the index in process, such as the benchmark. It is
     * valid until the next rebuild or [close].
     */
    public fun graph(): GraphReader = memory.reader()

    /** The lexical index that belongs to [graph]; valid as long as the graph is. */
    public fun searchIndex(): SearchIndex = memory.searchIndex()

    public fun analyze(
        task: String,
        stage: RetrievalStage = RetrievalStage.FULL,
        includeContextEstimates: Boolean = true,
        ml: MlMode = MlMode.ON,
    ): AnalysisResult {
        memory.load()
        val result = progress.stage(QueryStages.RANK) { TaskRetrieval(graph(), searchIndex()).analyze(task, stage, ml) }
        return if (includeContextEstimates) {
            progress.stage(QueryStages.ESTIMATE) { withContextEstimates(result) }
        } else {
            result
        }
    }

    public fun symbol(query: String): SymbolResult {
        memory.load()
        return progress.stage(QueryStages.SEARCH) { TaskRetrieval(graph(), searchIndex()).symbol(query) }
    }

    public fun impact(node: String): ImpactResult {
        memory.requireIndexed()
        val narrow = progress.stage(QueryStages.IMPACT) { ImpactClosure(graph(), searchIndex()).analyze(node) }
        if (narrow != null) return narrow
        memory.load()
        return progress.stage(QueryStages.IMPACT) { TaskRetrieval(graph(), searchIndex()).impact(node) }
    }

    public fun related(node: String): RelatedResult {
        memory.load()
        return progress.stage(QueryStages.RELATED) { GraphQueries(graph()).related(node) }
    }

    public fun graphView(
        scope: GraphScope = GraphScope.MODULES,
        node: String? = null,
        depth: Int = 1,
        limit: Int = GraphQueries.DEFAULT_LIMIT,
    ): GraphResult {
        memory.load()
        return progress.stage(QueryStages.GRAPH_VIEW) { GraphQueries(graph()).graph(scope, node, depth, limit) }
    }

    override fun close() {
        engine.close()
    }

    public companion object {
        private const val INDEX_FILE_NAME = "index.db"

        public fun toolVersion(): String = ToolVersion.value

        /**
         * The settings of the workspace at [root] as `info` shows them, read from their file now. It opens neither the
         * workspace nor its index, so it is safe to call while a scan runs.
         */
        public fun settingsReport(root: Path): SettingsReport =
            SettingsSource(root.toAbsolutePath().normalize(), null, System::getenv).report(IndexedScope.NOT_INDEXED)

        /** The freshness settings of the workspace at [root], read from their file now; it opens nothing. */
        public fun freshnessOf(root: Path): FreshnessReading =
            SettingsFile(root.toAbsolutePath().normalize()).freshness()

        /** Opens the workspace at [root] with the default capabilities; nothing is read until a use case runs. */
        public fun open(root: Path, options: OpenOptions = OpenOptions()): KrosScope =
            open(root, options, System::getenv)

        internal fun open(
            root: Path,
            options: OpenOptions,
            environment: (String) -> String?,
            trust: TrustStore = TrustStore.ofThisMachine(environment = environment),
        ): KrosScope {
            val absoluteRoot = root.toAbsolutePath().normalize()
            val index = (options.indexDirectory ?: absoluteRoot.resolve(WorkspaceConfig.DEFAULT_INDEX_DIRECTORY))
                .toAbsolutePath().normalize()
            val settings = SettingsSource(absoluteRoot, options.settings, environment, trust)
            val levels = LevelInForce(settings, if (options.trustBuild) ScanLevel.EXACT else options.level)
            val repositories = RepositoryRegistry.ofThisMachine(environment = environment)
            val registered = { repositories.registered(absoluteRoot) }
            val external = ExternalCheckouts(
                absoluteRoot,
                repositories,
                ScannedCheckouts.ofThisMachine(environment = environment),
            )
            val config = WorkspaceConfig(
                absoluteRoot,
                index,
                scope = settings::scope,
                disabledAnalyzers = { settings.analyzers().disabledKeys() },
                level = { levels.current().inEngine },
                registered = registered,
                checkoutsAccepted = external::accepted,
                artifactFolders = { repositories.artifactFolders(absoluteRoot) },
            )
            val registry = SelectedRegistry(settings::analyzers, levels::current) { analyzers, level ->
                DefaultCapabilities.registry(index, settings::workerSettings, analyzers, level)
            }
            val engine = ScanEngine(registry::get, config, options.progress.toEngine())
            val tokenCounts = TokenCountFile(index.resolve(TokenCountFile.FILE_NAME)).takeIf { options.tokenCache }
            val location = WorkspaceLocation(absoluteRoot, index, external)
            return KrosScope(engine, location, options.progress, tokenCounts, levels)
        }
    }
}

private fun emptyInfo(indexLocation: String, settings: SettingsReport) = InfoResult(
    toolVersion = ToolVersion.value,
    indexLocation = indexLocation,
    indexed = false,
    structure = StructureSummary(0, 0, 0, 0, 0, 0),
    artifactsByType = emptyMap(),
    symbolsByKind = emptyMap(),
    coverage = emptyList(),
    diagnostics = 0,
    metricsByName = emptyMap(),
    searchIndexPopulated = false,
    settings = settings,
)
