package io.github.krosstack.kscope.analyzer.schema.generator.specs

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path

private val specTypes = setOf(SchemaArtifactTypes.openApi, SchemaArtifactTypes.proto)

/** The spec files of every repository of a workspace, OpenAPI documents and proto files, by where they lie on disk. */
internal class SpecFiles(files: WorkspaceFiles, artifacts: List<Artifact>) {
    private val located: List<Pair<Path, Artifact>> = artifacts.filter { it.type in specTypes }
        .sortedWith(compareBy({ it.ref.repository.value }, { it.ref.path.value }))
        .map { files.absolutePath(it.ref).normalize() to it }

    fun openApiAt(path: Path): Artifact? = located
        .firstOrNull { (location, artifact) -> artifact.type == SchemaArtifactTypes.openApi && location == path }
        ?.second

    fun protosBelow(path: Path): List<Artifact> = located
        .filter { (location, artifact) -> artifact.type == SchemaArtifactTypes.proto && location.startsWith(path) }
        .map { it.second }
}
