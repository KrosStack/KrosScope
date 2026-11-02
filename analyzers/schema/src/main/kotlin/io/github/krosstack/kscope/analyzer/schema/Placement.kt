package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.BuildModel

/** The module and source set that an artifact belongs to, and the identities and scope of the symbols made for it. */
internal class Placement(val module: Module, val sourceSet: SourceSet?) {
    val identity = IdentityFactory(module.id, sourceSet?.symbolName())
    val scope = Scope(sourceSet = sourceSet?.id)

    companion object {
        /** Null when no module contains the artifact. */
        fun of(artifact: Artifact, model: BuildModel): Placement? {
            val sourceSet = artifact.sourceSet?.let { id -> model.sourceSets.firstOrNull { it.id == id } }
            val module = sourceSet?.let { owner -> model.modules.firstOrNull { it.id == owner.module } }
                ?: moduleContaining(artifact.ref, model)
            return module?.let { Placement(it, sourceSet) }
        }

        private fun moduleContaining(ref: ArtifactRef, model: BuildModel): Module? {
            val roots = model.buildRoots.filter { it.repository == ref.repository }.map { it.id }.toSet()
            return model.modules.filter { it.buildRoot in roots && isInside(ref.path.value, it.directory.value) }
                .maxByOrNull { it.directory.value.length }
        }

        private fun isInside(path: String, directory: String): Boolean =
            directory.isEmpty() || path.startsWith("$directory/")
    }
}
