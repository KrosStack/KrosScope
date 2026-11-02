package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.detector.ArtifactClassifier
import io.github.krosstack.kscope.engine.discovery.roots.OutputRootInventory
import io.github.krosstack.kscope.engine.discovery.roots.RepositoryFiles
import io.github.krosstack.kscope.engine.discovery.roots.RootDirectories
import io.github.krosstack.kscope.engine.scope.PatternNotices
import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.engine.scope.ScopeFilter
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.ArtifactDetector
import java.nio.file.Path

/** Every classified file of the workspace, without source sets, sorted by path. */
internal class Inventory(
    val artifacts: List<Artifact>,
    val notices: List<Diagnostic>,
    val candidates: List<String> = emptyList(),
)

internal class DiscoveryStage(
    private val root: Path,
    private val lister: FileLister,
    private val detectors: List<ArtifactDetector>,
    private val scope: ScanScope = ScanScope.ALL,
    private val excludesSettingsFile: Boolean = true,
) {
    private val filter = ScopeFilter(scope)
    private val artifactFactory = ArtifactFactory(ArtifactClassifier(detectors, root))

    fun inventory(repository: Repository): Inventory {
        val listing = lister.list(excludesSettingsFile)
        val outcomes = listing.paths.associateWith { relative -> ContentScanner.scan(root.resolve(relative)) }
        val artifacts = outcomes.mapNotNull { (relative, result) ->
            (result as? ContentResult.Read)?.let { artifactFactory.create(repository, relative, it.content) }
        }
        val unreadable = outcomes.mapNotNull { (relative, result) ->
            (result as? ContentResult.Unreadable)?.let { artifactFactory.unreadableNotice(relative, it.reason) }
        }
        return Inventory(artifacts, listing.notices + unreadable, listing.candidates)
    }

    /**
     * Adds the files of generated roots, which Git ignores, once the build model has named the roots, and the files
     * of the output roots that detectors name for the classified files.
     */
    fun withBuildOutputs(repository: Repository, inventory: Inventory, sourceSets: List<SourceSet>): Inventory {
        val directories = RootDirectories(root, artifactFactory, filter)
        val generated = GeneratedRootInventory(directories).merge(repository, inventory, sourceSets)
        val outputs = OutputRootInventory(detectors, RepositoryFiles(root), directories, filter)
        val merged = outputs.merge(repository, generated)
        val artifacts = merged.artifacts.map { if (marksGenerated(it)) it.copy(generated = SETTINGS_GENERATED) else it }
        val unmatched = PatternNotices.forListing(scope, merged.candidates) +
            PatternNotices.forGenerated(scope, artifacts.map { it.ref.path.value })
        return Inventory(artifacts, merged.notices + unmatched, merged.candidates)
    }

    private fun marksGenerated(artifact: Artifact) =
        artifact.generated == null && filter.marksGenerated(artifact.ref.path.value)

    private companion object {
        val SETTINGS_GENERATED = GeneratedInfo("settings", null)
    }
}
