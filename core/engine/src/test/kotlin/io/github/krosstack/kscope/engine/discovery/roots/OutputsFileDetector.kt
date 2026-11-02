package io.github.krosstack.kscope.engine.discovery.roots

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.OutputRoot
import io.github.krosstack.kscope.spi.OutputRootRequest

private const val REPOSITORY_ROOT = "/"

/** Claims files named `outputs.txt`, whose lines are `<directory> <generator>`, and names those directories. */
internal class OutputsFileDetector : ArtifactDetector {
    override val id: String = "test.outputs-file"

    private val type = ArtifactType("test.outputs", ArtifactCategory.CONFIG)

    override fun detect(candidate: ArtifactCandidate): ArtifactType? =
        type.takeIf { candidate.path.value.substringAfterLast('/') == "outputs.txt" }

    override fun outputRoots(request: OutputRootRequest): List<OutputRoot> =
        request.artifacts.filter { it.type == type }.flatMap { artifact ->
            String(request.files.read(artifact.ref)).lines().filter { it.isNotBlank() }.map { line ->
                val (directory, generator) = line.split(' ')
                OutputRoot(RepoPath(directory.takeIf { it != REPOSITORY_ROOT }.orEmpty()), generator)
            }
        }
}
