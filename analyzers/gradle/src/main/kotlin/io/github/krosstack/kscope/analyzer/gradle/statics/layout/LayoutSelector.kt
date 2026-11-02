package io.github.krosstack.kscope.analyzer.gradle.statics.layout

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticDiagnostics
import io.github.krosstack.kscope.analyzer.gradle.statics.android.AndroidConfig
import io.github.krosstack.kscope.analyzer.gradle.statics.android.AndroidLayout
import io.github.krosstack.kscope.analyzer.gradle.statics.android.AndroidVariants
import io.github.krosstack.kscope.analyzer.gradle.statics.android.Flavor
import io.github.krosstack.kscope.analyzer.gradle.statics.android.FlavorDirectories
import io.github.krosstack.kscope.analyzer.gradle.statics.convention.ProjectScripts
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginTable
import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk

/** Chooses how the source sets of a project are laid out from the plugins it applies. */
internal class LayoutSelector(
    private val disk: WorkspaceDisk,
    private val directory: String,
    private val scripts: ProjectScripts,
    private val ids: Set<String>,
    private val android: AndroidConfig?,
) {
    private val blocks = scripts.sources.flatMap(SourceSetBlocks::of)

    private val directoryExists: (String) -> Boolean =
        { name -> disk.isDirectory(RepoPaths.join(RepoPaths.join(directory, "src"), name)) }

    fun select(): Layout = when {
        PluginTable.KOTLIN_MULTIPLATFORM in ids || PluginTable.ANDROID_KOTLIN_LIBRARY in ids -> multiplatform()
        ids.any { it in PluginTable.androidPlugins } -> androidLayout()
        ids.any { it in PluginTable.jvmPlugins } -> jvmLayout()
        else -> inferred()
    }

    private fun androidLayout(): Layout = android?.let(::planAndroid) ?: Layout.empty

    private fun planAndroid(config: AndroidConfig): Layout {
        val flavors = inferredFlavors(config)
        val effective = config.copy(flavors = config.flavors + flavors.map { Flavor(it, null) })
        val layout = AndroidLayout(directoryExists).plan(effective)
        if (flavors.isEmpty()) return layout
        val problem = StaticDiagnostics.flavorsInferred(directory, flavors)
        return Layout(layout.targets, layout.variants, layout.sourceSets, layout.problems + problem, inferred = true)
    }

    private fun inferredFlavors(config: AndroidConfig): List<String> {
        if (!config.hasComputedFlavors) return emptyList()
        val sourceRoot = RepoPaths.join(directory, "src")
        val directories = disk.children(sourceRoot).filter { disk.isDirectory(RepoPaths.join(sourceRoot, it)) }
        return FlavorDirectories.inferred(config, directories)
    }

    private fun jvmLayout(): Layout = JvmLayout(directoryExists).plan(blocks, PluginTable.JAVA_TEST_FIXTURES in ids)

    private fun inferred(): Layout {
        val sourceRoot = RepoPaths.join(directory, "src")
        val names = disk.children(sourceRoot).filter { disk.isDirectory(RepoPaths.join(sourceRoot, it)) }
        val sourceSets = names.map { SourceSetDraft(it, emptyList(), emptyList(), emptyList(), declared = false) }
        val problems = listOfNotNull(StaticDiagnostics.layoutInferred(directory).takeIf { sourceSets.isNotEmpty() })
        return Layout(emptyList(), emptyList(), sourceSets, problems, inferred = true)
    }

    private fun multiplatform(): Layout {
        val targets = KotlinTargets.read(scripts.sources, PluginTable.ANDROID_KOTLIN_LIBRARY in ids)
        val hasAndroidPlugin = ids.any { it in PluginTable.androidPlugins }
        val variants = android?.takeIf { hasAndroidPlugin }?.let(AndroidVariants::of).orEmpty()
        val layout = MultiplatformLayout(directoryExists).plan(targets, blocks, variants)
        val custom = KotlinTargets.customHierarchy(scripts.sources)
        val problem = custom?.let { (source, call) -> StaticDiagnostics.customHierarchy(source.location(call)) }
        return Layout(layout.targets, layout.variants, layout.sourceSets, listOfNotNull(problem))
    }
}
