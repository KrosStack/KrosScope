package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.statics.convention.ConventionScripts
import io.github.krosstack.kscope.analyzer.gradle.statics.convention.Conventions
import io.github.krosstack.kscope.analyzer.gradle.statics.convention.PluginRegistrationReader
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginReader
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.BuildPlan
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.BuildStructure
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.CatalogRegistry
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import io.github.krosstack.kscope.model.Diagnostic

/**
 * The convention plugins each build can apply: those of the builds it includes, directly or through the builds these
 * include, and of its own `buildSrc`. Unrelated builds of a workspace may define plugins of the same id, so no build
 * sees the plugins of a build it does not reach.
 */
internal class BuildConventions(
    private val structure: BuildStructure,
    private val inputs: ProviderInputs,
    private val catalogs: CatalogRegistry,
    private val outlines: ScriptOutlines,
    disk: WorkspaceDisk,
) {
    private val reader = PluginRegistrationReader(outlines, disk)
    private val registered = structure.plans.filter { it.included }
        .associateWith { reader.read(it, catalogs.resolverFor(it)) }
    private val conventions = structure.plans.associateWith(::load)

    val problems: List<Diagnostic> get() = reader.problems

    fun of(plan: BuildPlan): Conventions = conventions.getValue(plan)

    private fun load(plan: BuildPlan): Conventions {
        val builds = structure.includedBy(plan)
        val scripts = ConventionScripts.of(inputs.kotlinScripts, builds.map { it.root.directory.value }.toSet())
        val registrations = builds.flatMap { registered.getValue(it) }.distinctBy { it.id }.associateBy { it.id }
        val loaded = Conventions(scripts, registrations, inputs.kotlinSources, outlines)
        val plugins = PluginReader(catalogs.resolverFor(plan))
        val own = plan.projects.mapNotNull { it.script }.mapNotNull { outlines.sourceOf(it) }.map { it to plugins }
        ConventionLoader(loaded, outlines).load(own)
        return loaded
    }
}
