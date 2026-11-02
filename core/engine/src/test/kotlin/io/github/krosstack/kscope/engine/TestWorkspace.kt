package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.CapabilityRegistry
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.assertTrue

internal fun coreRegistry(
    vararg extra: Any,
    providers: List<BuildModelProvider> = CoreCapabilities.buildModelProviders(),
): CapabilityRegistry = CapabilityRegistry(
    detectors = CoreCapabilities.detectors() + extra.filterIsInstance<ArtifactDetector>(),
    buildModelProviders = providers,
    synthesizers = extra.filterIsInstance<SurfaceSynthesizer>(),
    producers = extra.filterIsInstance<FactProducer>(),
    linkers = extra.filterIsInstance<BoundaryLinker>(),
)

internal val ScanEngine.store: GraphStore get() = graph() as GraphStore

internal fun ScanEngine.canonicalDump(): String = CanonicalFacts.toJson(store.batches())

internal fun ScanEngine.artifactPaths(): Set<String> = graph().artifacts().map { it.ref.path.value }.toSet()

// Verification scans again into a scratch store, so producers run once more; call it after counting their calls.
internal fun ScanEngine.assertMatchesRebuild() {
    val verify = update(verify = true).verify
    assertTrue(verify?.matches == true, "Incremental differs from rebuild: ${verify?.differences}")
}

internal fun <T> scanningSample(registry: CapabilityRegistry, block: (TestWorkspace, ScanEngine) -> T): T =
    TestWorkspace("gradle-multi").use { workspace -> workspace.engine(registry).use { block(workspace, it) } }

/** A checked-in sample tree copied to a temporary directory, with the index kept outside of the tree. */
internal class TestWorkspace(sample: String? = null) : AutoCloseable {
    private val base: Path = Files.createTempDirectory("kscope-engine")
    val root: Path = base.resolve("workspace")
    val indexDirectory: Path = base.resolve("index")

    init {
        Files.createDirectories(root)
        sample?.let(::copySample)
    }

    fun engine(
        registry: CapabilityRegistry = coreRegistry(),
        maxArtifactBytes: Long? = null,
        trusted: Boolean = false,
        scope: () -> ScanScope = { ScanScope.ALL },
    ): ScanEngine {
        val limit = maxArtifactBytes ?: WorkspaceConfig.DEFAULT_MAX_ARTIFACT_BYTES
        val config = WorkspaceConfig(root, indexDirectory, trusted, limit, scope)
        return ScanEngine(registry, config)
    }

    fun write(relativePath: String, text: String) {
        val target = root.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    fun delete(relativePath: String) {
        Files.delete(root.resolve(relativePath))
    }

    fun move(from: String, to: String) {
        val target = root.resolve(to)
        Files.createDirectories(target.parent)
        Files.move(root.resolve(from), target, StandardCopyOption.ATOMIC_MOVE)
    }

    fun seedRepositoryId(id: String, directory: Path = indexDirectory) {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("repository-id"), id)
    }

    override fun close() {
        TemporaryTree.delete(base)
    }

    private fun copySample(sample: String) {
        val source = TestWorkspace::class.java.getResource("/samples/$sample")
        val sourceRoot = Path.of(checkNotNull(source) { "Missing sample $sample" }.toURI())
        Files.walk(sourceRoot).use { paths ->
            paths.forEach { path ->
                val target = root.resolve(sourceRoot.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target)
            }
        }
    }
}
