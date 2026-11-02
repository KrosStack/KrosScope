package io.github.krosstack.kscope.analyzer.schema.web.map

import io.github.krosstack.kscope.analyzer.schema.web.RepoPaths
import io.github.krosstack.kscope.analyzer.schema.web.WebArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.web.manifest.PackageManifestReader
import io.github.krosstack.kscope.analyzer.schema.web.output.BundleOutput
import io.github.krosstack.kscope.analyzer.schema.web.output.BundleOutputReader
import io.github.krosstack.kscope.analyzer.schema.web.settingsTextOf
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles

/**
 * Tells which package built a file of a bundle, from the hand-written manifests of one repository: the package whose
 * output directory holds the file, the innermost one if several do, and else the package whose directory is the
 * nearest above it. The manifests are read when the first answer is asked for.
 */
internal class PackageDirectories(
    private val artifacts: List<Artifact>,
    private val files: WorkspaceFiles,
    private val outputs: BundleOutputReader,
) {
    private val outputByDirectory: Map<String, BundleOutput?> by lazy {
        artifacts.filter { it.type == WebArtifactTypes.npmManifest && it.generated == null }
            .associate { manifest -> RepoPaths.directoryOf(manifest.ref.path.value) to outputOf(manifest) }
    }

    fun owning(path: String): String? {
        val byOutput = outputByDirectory.filterValues { it != null && RepoPaths.isInside(path, it.directory) }
            .maxByOrNull { it.value?.directory.orEmpty().length }
        return byOutput?.key
            ?: outputByDirectory.keys.filter { RepoPaths.isInside(path, it) }.maxByOrNull { it.length }
    }

    private fun outputOf(manifest: Artifact): BundleOutput? =
        files.settingsTextOf(manifest)?.let(PackageManifestReader::read)?.let { outputs.outputOf(manifest, it) }
}
