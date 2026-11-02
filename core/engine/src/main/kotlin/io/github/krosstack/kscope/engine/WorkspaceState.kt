package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.detector.ArtifactClassifier
import io.github.krosstack.kscope.engine.discovery.BlockingEntry
import io.github.krosstack.kscope.engine.discovery.BuildFileProbe
import io.github.krosstack.kscope.engine.discovery.FileLister
import io.github.krosstack.kscope.engine.discovery.GitAccess
import io.github.krosstack.kscope.engine.discovery.ProcessRunner
import io.github.krosstack.kscope.engine.discovery.WorkspaceLayout
import io.github.krosstack.kscope.engine.discovery.WorkspaceRepositories
import io.github.krosstack.kscope.engine.discovery.WorkspaceRepository
import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.engine.pipeline.LevelUnit
import io.github.krosstack.kscope.engine.scope.AnalyzerUnit
import io.github.krosstack.kscope.engine.scope.ScopeUnit
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.spi.graph.GraphStore
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Digests the scan's file listing and metadata and where the HEAD of each repository points, without reading file
 * contents or opening the index.
 */
public fun ScanEngine.workspaceStamp(): Fingerprint = workspaceStamp(ProcessRunner())

internal fun ScanEngine.workspaceStamp(runner: ProcessRunner): Fingerprint {
    val layout = WorkspaceRepositories(
        config.root,
        config.indexDirectory,
        runner,
        gitExecutable,
    ).resolve(config.registered())
    return stampOf(layout.repositories)
}

private fun ScanEngine.scopeKey(): String = ScopeUnit.keyOf(config.scope())?.value.orEmpty() +
    AnalyzerUnit.keyOf(config.disabledAnalyzers())?.value.orEmpty() +
    LevelUnit.plan(config.level())?.fingerprint?.value.orEmpty()

private fun ScanEngine.stampOf(repositories: List<WorkspaceRepository>): Fingerprint {
    val scope = config.scope()
    val listings = repositories.map { repository ->
        val root = repository.root
        val buildFiles = BuildFileProbe(ArtifactClassifier(registry.detectors, root), root)
        val lister = FileLister(
            root,
            config.indexDirectory,
            repository.git,
            scope,
            buildFiles::isBuildFile,
        )
        val paths = lister.list(root == config.root).paths
        StampListing(root, repository.directory.value, paths, repository.headToken())
    }
    return workspaceStamp(listings, scopeKey())
}

/** Reuses workspace observation resources without opening the index. */
public class WorkspaceState internal constructor(private val engine: ScanEngine, runner: ProcessRunner) {
    private val repositories = WorkspaceRepositories(
        engine.config.root,
        engine.config.indexDirectory,
        runner,
        engine.gitExecutable,
        resolveDirectory = true,
    )
    private var layout: WorkspaceLayout = repositories.resolve(engine.config.registered())

    /** The Git directory of the repository of the root, or of the first repository of several. */
    public val gitDirectory: Path? get() = gitDirectories.firstOrNull()

    /** The Git directory of every repository of the workspace, in directory order. */
    public val gitDirectories: List<Path> get() = current().repositories.mapNotNull { gitDirectoryOf(it) }

    public fun stamp(): Fingerprint = engine.stampOf(current().repositories)

    private fun current(): WorkspaceLayout {
        val registered = engine.config.registered()
        if (layout.registrations.requested != registered || isStale()) layout = repositories.resolve(registered)
        return layout
    }

    private fun isStale(): Boolean = searchedDirectoriesChanged() ||
        layout.withoutCommit.any(repositories::hasCommit) ||
        layout.registrations.unavailable.any { repositories.hasCommitAt(it.root) } ||
        layout.registrations.blocked.any(::entryChanged)

    private fun entryChanged(entry: BlockingEntry): Boolean =
        !Files.exists(entry.path) || Files.isDirectory(entry.path) != entry.directory

    private fun searchedDirectoriesChanged(): Boolean =
        layout.candidates?.let { it != repositories.candidateDirectories() } ?: false

    private fun gitDirectoryOf(repository: WorkspaceRepository): Path? =
        (repository.git as? GitAccess.WorkTree)?.directory
}

/** Creates an observer whose stamps never read file contents or open the index. */
public fun ScanEngine.workspaceState(): WorkspaceState = workspaceState(ProcessRunner())

internal fun ScanEngine.workspaceState(runner: ProcessRunner): WorkspaceState = WorkspaceState(this, runner)

internal class StampListing(val root: Path, val directory: String, val paths: List<String>, val head: String = "")

internal fun workspaceStamp(root: Path, files: List<String>, scopeKey: String = ""): Fingerprint =
    workspaceStamp(listOf(StampListing(root, "", files)), scopeKey)

internal fun workspaceStamp(listings: List<StampListing>, scopeKey: String = ""): Fingerprint {
    val digest = FingerprintBuilder()
    if (scopeKey.isNotEmpty()) digest.add(scopeKey)
    for (listing in listings) {
        if (listing.head.isNotEmpty()) digest.add("head:${listing.head}")
        for (path in listing.paths) {
            digest.add(if (listing.directory.isEmpty()) path else "${listing.directory}/$path")
            digestAttributes(digest, listing.root.resolve(path))
        }
    }
    return digest.build()
}
private fun digestAttributes(digest: FingerprintBuilder, file: Path) {
    runCatching {
        Files.readAttributes(file, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    }.onSuccess {
        digest.add(it.size().toString()).add(it.lastModifiedTime().toString())
    }.onFailure {
        digest.add("attributes-unavailable")
    }
}

/** Whether a persisted repository model belongs to a registered provider that requires build execution. */
public fun ScanEngine.indexRequiresTrust(): Boolean {
    if (!hasIndexedFiles()) return false
    val trusted = registry.buildModelProviders.filter { it.requiresTrust }.map { it.producer.id }.toSet()
    return (graph() as GraphStore).units().any {
        it.owner.kind == OwnershipKind.REPOSITORY && it.producer.id in trusted
    }
}

/** The level the persisted index was built at, or null when the workspace has no index with files in it. */
public fun ScanEngine.indexLevel(): ScanLevel? = when {
    !hasIndexedFiles() -> null
    indexRequiresTrust() -> ScanLevel.EXACT
    LevelUnit.recordedIn(graph() as GraphStore) -> ScanLevel.SYNTAX
    else -> ScanLevel.RESOLVED
}
