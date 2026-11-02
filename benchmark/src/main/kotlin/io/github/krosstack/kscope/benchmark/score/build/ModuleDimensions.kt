package io.github.krosstack.kscope.benchmark.score.build

import io.github.krosstack.kscope.benchmark.gold.GoldBuildModel
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.model.Module

private const val COMPILE_SDK_ATTRIBUTE = "android.compileSdk"

/** The modules of the build, and the builds that are included in it. */
internal class ModuleDimensions(private val index: GraphIndex) : DimensionSource {
    private val ownModules: List<Module> = index.structure.modules.filterNot(index::isBuildLogic)
    private val buildLogic: List<Module> = index.structure.modules.filter(index::isBuildLogic)

    override fun dimensions(gold: GoldBuildModel): List<Dimension> = listOf(
        Dimension(
            "modules",
            gold.modules.map { Item(it.path, it.path) },
            ownModules.map { index.goldPathOf(it).let { path -> Item(path, path) } },
        ),
        Dimension(
            "included builds",
            gold.includedBuilds.map { Item(it, it) },
            buildLogic.map { Item(it.directory.value, it.directory.value) },
        ),
    )
}

/** What each module applies and declares about Android: plugins, namespace and compile SDK. */
internal class PluginDimensions(private val index: GraphIndex) : DimensionSource {
    private val ownModules: List<Module> = index.structure.modules.filterNot(index::isBuildLogic)

    override fun dimensions(gold: GoldBuildModel): List<Dimension> = listOf(
        Dimension(
            "plugins",
            gold.modules.flatMap { module ->
                (module.appliedPlugins + module.conventionPlugins).map { fact(module.path, "applies $it") }
            },
            ownModules.flatMap { module -> module.plugins.map { fact(index.goldPathOf(module), "applies $it") } },
        ),
        Dimension(
            "android namespace",
            gold.modules.mapNotNull { module -> module.android?.namespace?.let { fact(module.path, it) } },
            ownModules.mapNotNull { module -> module.androidNamespace?.let { fact(index.goldPathOf(module), it) } },
        ),
        Dimension(
            "android compileSdk",
            gold.modules.mapNotNull { module -> module.android?.compileSdk?.let { fact(module.path, "$it") } },
            ownModules.mapNotNull { module ->
                module.attributes[COMPILE_SDK_ATTRIBUTE]?.let { fact(index.goldPathOf(module), it) }
            },
        ),
    )
}
