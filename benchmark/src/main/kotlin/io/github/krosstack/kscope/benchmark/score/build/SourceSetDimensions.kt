package io.github.krosstack.kscope.benchmark.score.build

import io.github.krosstack.kscope.benchmark.gold.GoldBuildModel
import io.github.krosstack.kscope.benchmark.gold.GoldSourceSet
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSet

private val platformNames = mapOf(
    Platform.COMMON to "common",
    Platform.JVM to "jvm",
    Platform.ANDROID to "androidJvm",
    Platform.NATIVE to "native",
    Platform.JS to "js",
    Platform.WASM to "wasm",
)

/** The compilation targets and the build variants of each module. */
internal class TargetDimensions(private val index: GraphIndex) : DimensionSource {
    private val ownModules: List<Module> = index.structure.modules.filterNot(index::isBuildLogic)

    override fun dimensions(gold: GoldBuildModel): List<Dimension> = listOf(
        Dimension(
            "targets",
            gold.modules.flatMap { module -> module.targets.map { fact(module.path, "${it.name}:${it.platform}") } },
            ownModules.flatMap { module ->
                index.structure.targets.filter { it.module == module.id }
                    .map { fact(index.goldPathOf(module), "${it.name}:${platformNames.getValue(it.platform)}") }
            },
        ),
        Dimension(
            "variants",
            gold.modules.flatMap { module -> module.variants.map { fact(module.path, it) } },
            ownModules.flatMap { module ->
                index.structure.variants.filter { it.module == module.id }
                    .map { fact(index.goldPathOf(module), it.name) }
            },
        ),
    )
}

/** The source sets of each module, how they refine one another and which directories they read. */
internal class SourceSetDimensions(index: GraphIndex) : DimensionSource {
    private val sourceSets: List<Pair<String, SourceSet>> = index.structure.modules
        .filterNot(index::isBuildLogic)
        .flatMap { module ->
            index.structure.sourceSets.filter { it.module == module.id }.map { index.goldPathOf(module) to it }
        }

    override fun dimensions(gold: GoldBuildModel): List<Dimension> = listOf(
        Dimension(
            "source sets",
            gold.modules.flatMap { module -> module.sourceSets.map { fact(module.path, it.name) } },
            sourceSets.map { (module, sourceSet) -> fact(module, sourceSet.name) },
        ),
        Dimension(
            "source set dependsOn",
            gold.modules.flatMap { module ->
                module.sourceSets.flatMap { set -> set.dependsOn.map { fact(module.path, "${set.name}>$it") } }
            },
            sourceSets.flatMap { (module, sourceSet) ->
                sourceSet.dependsOn.map { fact(module, "${sourceSet.name}>${it.value.substringAfterLast('@')}") }
            },
        ),
        Dimension(
            "source roots",
            gold.modules.flatMap { module ->
                module.sourceSets.flatMap { set -> directoriesOf(set).map { fact(module.path, "${set.name}:$it") } }
            },
            sourceSets.flatMap { (module, sourceSet) ->
                sourceSet.roots.filterNot { it.generated }
                    .map { fact(module, "${sourceSet.name}:${it.directory.value}") }
            },
        ),
    )

    // Gold separates source, resource and manifest paths by role; the graph keeps all of them as roots.
    private fun directoriesOf(sourceSet: GoldSourceSet): List<String> =
        sourceSet.sourceDirs + sourceSet.resourceDirs + listOfNotNull(sourceSet.manifest)
}
