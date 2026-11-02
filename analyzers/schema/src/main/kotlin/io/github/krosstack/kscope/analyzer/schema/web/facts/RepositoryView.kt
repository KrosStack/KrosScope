package io.github.krosstack.kscope.analyzer.schema.web.facts

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.web.WebArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.web.map.PackageDirectories
import io.github.krosstack.kscope.analyzer.schema.web.output.BundleOutputReader
import io.github.krosstack.kscope.analyzer.schema.web.textOf
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.OutputRoot
import io.github.krosstack.kscope.spi.ProduceRequest

/**
 * The files of the repository of one unit, as the facts of the unit see them: the artifacts by path, the readers of
 * outputs and packages built over them, and where an artifact belongs. The artifacts are those that the engine hands to
 * all producers and those of the unit itself. The scripts are the hand-written ones that some unit gives a symbol,
 * which are those that a module contains.
 */
internal class RepositoryView(private val request: ProduceRequest, unit: AnalysisUnit) {
    private val repository = unit.artifacts.first().ref.repository

    val artifacts: Map<String, Artifact> = (request.artifacts + unit.artifacts)
        .filter { it.ref.repository == repository }
        .associateBy { it.ref.path.value }

    val scripts: Map<String, Artifact> by lazy {
        artifacts.filterValues {
            it.type in WebArtifactTypes.scripts && it.generated == null && placementOf(it) != null
        }
    }

    val outputs: BundleOutputReader by lazy { BundleOutputReader(artifacts.values.toList(), request.files) }

    val packages: PackageDirectories by lazy {
        PackageDirectories(artifacts.values.toList(), request.files, outputs)
    }

    fun placementOf(artifact: Artifact): Placement? = Placement.of(artifact, request.model)

    fun textOf(artifact: Artifact): String? = request.files.textOf(artifact)

    fun isOutput(artifact: Artifact): Boolean =
        artifact.generated?.generator?.startsWith(OutputRoot.LABEL_PREFIX) == true
}
