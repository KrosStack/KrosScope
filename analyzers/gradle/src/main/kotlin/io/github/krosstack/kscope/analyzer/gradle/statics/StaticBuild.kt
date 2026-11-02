package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.statics.convention.Conventions
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.upperFirst
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginReader
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.BuildPlan
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.BuildStructure
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.BuildStructureReader
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.CatalogRegistry
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.GeneratedRoots
import io.github.krosstack.kscope.analyzer.gradle.workspace.ConventionRoots
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceRoot
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult

private const val NOT_A_MODULE = "gradle.static.project-not-a-module"

private val diagnosticOrder = compareBy<Diagnostic>(
    { it.code.value },
    { it.location?.artifact?.path?.value.orEmpty() },
    { it.location?.range?.startLine ?: 0 },
    { it.message },
)

/** One run of the static provider over the artifacts of a request. */
internal class StaticBuild(private val request: BuildModelRequest, private val outlines: ScriptOutlines) {
    fun run(): BuildModelResult {
        val inputs = ProviderInputs(request.artifacts)
        if (inputs.settings.isEmpty()) return BuildModelResult.NotApplicable
        outlines.prepare(inputs.settings + inputs.buildScripts)
        val structure = BuildStructureReader(request.repository.id, inputs, outlines).read()
        return if (structure.hasSettings) provide(inputs, structure) else BuildModelResult.NotApplicable
    }

    private fun provide(inputs: ProviderInputs, structure: BuildStructure): BuildModelResult {
        val catalogs = CatalogRegistry(structure.plans, inputs.catalogs, request.files)
        val disk = WorkspaceDisk(WorkspaceRoot.of(request.files, inputs.settings.first()))
        val conventions = BuildConventions(structure, inputs, catalogs, outlines, disk)
        val models = structure.plans.flatMap { plan ->
            val resolver = catalogs.resolverFor(plan)
            val scope = BuildScope(plan, resolver, accessorsOf(plan), conventions.of(plan), outlines, disk)
            plan.projects.map(ModuleBuilder(scope)::build)
        }
        return resultOf(structure, catalogs, conventions.problems, models, disk)
    }

    private fun accessorsOf(plan: BuildPlan): Map<String, String> =
        plan.projects.filter { it.path != ":" }.associate { project ->
            val segments = project.path.trim(':').split(':')
            segments.joinToString(".") { camelCase(it) } to project.path
        }

    private fun camelCase(segment: String): String {
        val parts = segment.split('-', '_')
        return parts.first() + parts.drop(1).joinToString("") { it.upperFirst() }
    }

    private fun resultOf(
        structure: BuildStructure,
        catalogs: CatalogRegistry,
        conventionProblems: List<Diagnostic>,
        models: List<ProjectModel>,
        disk: WorkspaceDisk,
    ): BuildModelResult {
        val modules = models.mapNotNull { it.module }
        val moduleIds = modules.map { it.id.value }.toSet()
        val model = BuildModel(
            buildRoots = structure.plans.map { it.root }.sortedBy { it.id.value },
            modules = modules.sortedBy { it.id.value },
            targets = models.flatMap { it.targets }.sortedBy { it.id.value },
            variants = models.flatMap { it.variants }.sortedBy { it.id.value },
            sourceSets = models.flatMap { it.sourceSets }.sortedBy { it.id.value },
            dependencies = models.flatMap { it.dependencies }.map { checkedLink(it, moduleIds) }
                .sortedBy { KscopeJson.encodeToString(Relationship.serializer(), it) },
        )
        val withRoots = GeneratedRoots(disk).add(ConventionRoots(disk).supplement(model))
        val diagnostics = (structure.problems + catalogs.problems + conventionProblems + models.flatMap { it.problems })
            .distinct().sortedWith(diagnosticOrder)
        val references = models.flatMap { it.references }.distinct().map { Fact.OfRelationship(it) }
        val facts = (catalogs.symbols + references).sortedBy { KscopeJson.encodeToString(Fact.serializer(), it) }
        return BuildModelResult.Provided(withRoots, diagnostics, facts = facts)
    }

    private fun checkedLink(edge: Relationship, moduleIds: Set<String>): Relationship {
        val target = (edge.resolution as? Resolution.Resolved)?.target as? NodeRef.OfModule
        return if (target == null || target.id.value in moduleIds) {
            edge
        } else {
            edge.copy(resolution = Resolution.Unresolved(target.id.value, ReasonCode(NOT_A_MODULE)))
        }
    }
}

/**
 * Outlines the convention plugin scripts and the plugin class sources that the build scripts apply, and those that
 * these apply in turn.
 */
internal class ConventionLoader(private val conventions: Conventions, private val outlines: ScriptOutlines) {
    fun load(sources: List<Pair<ScriptSource, PluginReader>>) {
        val seen = mutableSetOf<String>()
        var frontier = sources
        while (frontier.isNotEmpty()) {
            val ids = frontier.flatMap { (source, reader) ->
                reader.read(source).uses.filter { it.applied }.map { it.id to reader }
            }.filter { (id, _) -> seen.add(id) }
            outlines.prepare(ids.flatMap { (id, _) -> conventions.filesToOutline(id) })
            frontier = ids.mapNotNull { (id, reader) -> conventions.conventionFor(id)?.source?.let { it to reader } }
        }
    }
}
