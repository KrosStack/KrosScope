package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.ScanLevel
import io.github.krosstack.kscope.engine.WorkspaceConfig
import io.github.krosstack.kscope.engine.discovery.WorkspaceLayout
import io.github.krosstack.kscope.engine.progress.ProgressListener
import io.github.krosstack.kscope.engine.progress.forVerification
import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.CapabilityRegistry
import io.github.krosstack.kscope.spi.RepositoryCheckout
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Files
import java.nio.file.Path

/**
 * The fixed inputs of a scan: what to scan with, where, and which repositories. The scope, the analyzer switches,
 * the level and the artifact folders are asked for once.
 */
internal class ScanContext(
    val registry: CapabilityRegistry,
    val config: WorkspaceConfig,
    val layout: WorkspaceLayout,
    val progress: ProgressListener = ProgressListener.NONE,
) {
    val scope: ScanScope = config.scope()
    val disabledAnalyzers: Set<String> = config.disabledAnalyzers()
    val level: ScanLevel = config.level()
    val artifactFolders: List<Path> = config.artifactFolders()

    val repositories = layout.repositories

    val checkouts: List<RepositoryCheckout> = layout.repositories.map { it.checkout }

    /** The same scan, reporting its stages as those of the clean rebuild that verification compares to. */
    fun forVerification(): ScanContext = ScanContext(registry, config, layout, progress.forVerification())
}

internal class WorkspaceFileAccess(checkouts: List<RepositoryCheckout>) : WorkspaceFiles {
    private val roots: Map<RepositoryId, Path> = checkouts.associate { it.repository.id to it.root }

    override fun read(ref: ArtifactRef): ByteArray = Files.readAllBytes(absolutePath(ref))

    override fun absolutePath(ref: ArtifactRef): Path =
        checkNotNull(roots[ref.repository]) { "No repository ${ref.repository.value} in this workspace" }
            .resolve(ref.path.value)
}
