package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.build.BuildModelCodec
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel

/** Joins the build models of the repositories of a workspace into one, ordered by identity as every model is. */
internal object BuildModelMerge {
    fun merge(models: List<ResolvedBuildModel>): ResolvedBuildModel {
        if (models.size == 1) return models.single()
        val parts = models.map { it.model }
        val joined = BuildModel(
            buildRoots = parts.flatMap { it.buildRoots },
            modules = parts.flatMap { it.modules },
            targets = parts.flatMap { it.targets },
            variants = parts.flatMap { it.variants },
            sourceSets = parts.flatMap { it.sourceSets },
            dependencies = parts.flatMap { it.dependencies },
        )
        val classpaths = models.fold(emptyMap<SourceSetId, List<String>>()) { all, model -> all + model.classpaths }
        return ResolvedBuildModel(BuildModelCodec.canonical(joined), classpaths)
    }
}
