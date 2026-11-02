package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.web.manifest.PackageManifestReader
import io.github.krosstack.kscope.analyzer.schema.web.output.BundleOutputReader
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.OutputRoot
import io.github.krosstack.kscope.spi.OutputRootRequest

private const val FILE_NAME = "package.json"
private const val DEPENDENCIES_DIRECTORY = "node_modules"

/**
 * Claims the `package.json` files that are not installed dependencies, and names the directories that their bundlers
 * write to, as [BundleOutputReader] reads them: the build of a package leaves a directory that Git ignores, and the
 * bundle in it is read from disk once it exists.
 */
internal class NpmManifestDetector : ArtifactDetector {
    override val id: String = WebArtifactTypes.npmManifest.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val segments = candidate.path.value.split('/')
        val isManifest = segments.last().lowercase() == FILE_NAME && DEPENDENCIES_DIRECTORY !in segments.dropLast(1)
        return WebArtifactTypes.npmManifest.takeIf { isManifest }
    }

    override fun outputRoots(request: OutputRootRequest): List<OutputRoot> {
        val reader = BundleOutputReader(request.artifacts, request.files)
        return request.artifacts.filter { it.type == WebArtifactTypes.npmManifest && it.generated == null }
            .mapNotNull { outputOf(it, reader, request) }
            .map { OutputRoot(RepoPath(it.directory), it.bundler.id) }
    }

    private fun outputOf(manifest: Artifact, reader: BundleOutputReader, request: OutputRootRequest) =
        request.files.settingsTextOf(manifest)?.let(PackageManifestReader::read)
            ?.let { reader.outputOf(manifest, it) }
}
