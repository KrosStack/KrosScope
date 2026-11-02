package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole

/** Takes the files of source sets whose role is not main out of an inventory whose files have their source set. */
internal object TestFiles {
    fun without(artifacts: List<Artifact>, sourceSets: List<SourceSet>): List<Artifact> {
        val outsideMain = sourceSets.filter { it.role != SourceSetRole.MAIN }.mapTo(HashSet()) { it.id }
        return artifacts.filter { it.sourceSet !in outsideMain }
    }
}
