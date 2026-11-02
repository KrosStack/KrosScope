package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogResolver
import io.github.krosstack.kscope.analyzer.gradle.mapping.ModuleTraits
import io.github.krosstack.kscope.analyzer.gradle.statics.android.AndroidConfig
import io.github.krosstack.kscope.analyzer.gradle.statics.android.AndroidReader
import io.github.krosstack.kscope.analyzer.gradle.statics.convention.ConventionExpander
import io.github.krosstack.kscope.analyzer.gradle.statics.convention.Conventions
import io.github.krosstack.kscope.analyzer.gradle.statics.convention.ProjectScripts
import io.github.krosstack.kscope.analyzer.gradle.statics.dependency.CatalogReferences
import io.github.krosstack.kscope.analyzer.gradle.statics.dependency.DeclaredEdges
import io.github.krosstack.kscope.analyzer.gradle.statics.dependency.DependencyDeclaration
import io.github.krosstack.kscope.analyzer.gradle.statics.dependency.DependencyEdges
import io.github.krosstack.kscope.analyzer.gradle.statics.dependency.DependencyNotations
import io.github.krosstack.kscope.analyzer.gradle.statics.dependency.DependencyReader
import io.github.krosstack.kscope.analyzer.gradle.statics.dependency.FileAnchors
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.LayoutSelector
import io.github.krosstack.kscope.analyzer.gradle.statics.layout.ModuleParts
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.ArchiveReader
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.ComposeResourceReader
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginReader
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginTable
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PluginUse
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.ProjectGroup
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.ProjectValues
import io.github.krosstack.kscope.analyzer.gradle.statics.plugin.PublicationReader
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.BuildPlan
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.ProjectPlan
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** Everything the static provider derived for one project. */
internal data class ProjectModel(
    val module: Module?,
    val targets: List<Target>,
    val variants: List<Variant>,
    val sourceSets: List<SourceSet>,
    val dependencies: List<Relationship>,
    val references: List<Relationship>,
    val problems: List<Diagnostic>,
)

/** What building the projects of one build needs. */
internal data class BuildScope(
    val plan: BuildPlan,
    val catalogs: CatalogResolver,
    val projectAccessors: Map<String, String>,
    val conventions: Conventions,
    val outlines: ScriptOutlines,
    val disk: WorkspaceDisk,
)

private typealias DeclaredDependencies = List<Pair<DependencyDeclaration, DeclaredEdges>>

private val crossProjectBlocks = setOf("subprojects", "allprojects")
private val configuringCalls = setOf("apply", "plugins", "dependencies", "android", "kotlin", "java", "sourceSets")

/** A project with the scripts that configure it and the ids of the plugins they apply. */
private class ConfiguredProject(
    val plan: ProjectPlan,
    val scripts: ProjectScripts,
    val ids: Set<String>,
    val outputAttributes: Map<String, String>,
)

/**
 * Builds the module of a project from its build script and the convention plugins it applies. A project that applies
 * no plugin and has no source directory is not a module, but what its script reads from the version catalogs is still
 * recorded. A project whose plugins are not known has the source sets of its directories, found by convention.
 */
internal class ModuleBuilder(private val scope: BuildScope) {
    private val plugins = PluginReader(scope.catalogs)
    private val expander = ConventionExpander(scope.conventions, plugins)
    private val android = AndroidReader(scope.catalogs)
    private val notations = DependencyNotations(scope.catalogs, scope.projectAccessors)
    private val projectPaths = scope.plan.projects.map { it.path }.toSet()
    private val values = ProjectValues(scope.disk, scope.plan.root.directory.value)
    private val publications = PublicationReader(values, scope.plan.settings?.rootName)
    private val archives = ArchiveReader(values, scope.plan.settings?.rootName)

    fun build(project: ProjectPlan): ProjectModel {
        val own = project.script?.let { scope.outlines.sourceOf(it) }
        val scripts = own?.let(expander::expand) ?: ProjectScripts(emptyList(), emptyList(), emptyList())
        val applied = scripts.uses.filter { it.applied }
        val ids = (applied.map { it.id } + applied.flatMap { PluginTable.impliedBy(it.id) }).toSortedSet()
        val missing = listOfNotNull(project.script.takeIf { own == null }?.let(StaticDiagnostics::scriptNotAnalysed))
        val publication = publications.read(project.path, project.directory, ids, scripts.sources)
        val problems = missing + scripts.problems + crossProjectProblems(own) + publication.problems
        val attributes = publication.attributes + archives.read(project.path, project.directory, ids, scripts.sources)
        return assemble(ConfiguredProject(project, scripts, ids, attributes), applied, problems)
    }

    private fun crossProjectProblems(own: ScriptSource?): List<Diagnostic> = own?.let { source ->
        source.calls.filter { it.receiver == null && it.name in crossProjectBlocks && it.body.any(::configuresProject) }
            .map { ScriptDiagnostics.crossProjectConfiguration(it.name, source.location(it)) }
    }.orEmpty()

    private fun configuresProject(call: ScriptCall) = call.name in configuringCalls

    private fun assemble(
        project: ConfiguredProject,
        applied: List<PluginUse>,
        problems: List<Diagnostic>,
    ): ProjectModel {
        val moduleId = ModuleId(scope.plan.root.id.value + project.plan.path)
        val config = if (hasAndroid(project.ids)) android.read(project.scripts.sources) else null
        val layout = LayoutSelector(scope.disk, project.plan.directory, project.scripts, project.ids, config).select()
        val parts = ModuleParts(moduleId, layout)
        val declared = declaredDependencies(moduleId, project, parts.sourceSets)
        val references = project.scripts.uses.mapNotNull { use ->
            use.reference?.let { CatalogReferences.relationship(use.source, use.call, it, false) }
        }
        val unanalysed = applied.distinctBy { it.id }.filter { isNotAnalysed(it.id) }
            .map { ScriptDiagnostics.pluginNotAnalysed(it.id, it.source.location(it.call)) }
        val isModule = project.ids.isNotEmpty() || layout.sourceSets.isNotEmpty()
        return ProjectModel(
            module = if (isModule) moduleOf(project, config, moduleId, layout.inferred, declared) else null,
            targets = parts.targets,
            variants = parts.variants,
            sourceSets = parts.sourceSets,
            dependencies = if (isModule) declared.flatMap { it.second.dependencies } else emptyList(),
            references = references + declared.flatMap { it.second.references },
            problems = problems + layout.problems + config?.problems.orEmpty() + unanalysed +
                declared.flatMap { it.second.problems },
        )
    }

    private fun hasAndroid(ids: Set<String>) =
        ids.any { it in PluginTable.androidPlugins } || PluginTable.ANDROID_KOTLIN_LIBRARY in ids

    private fun isNotAnalysed(id: String) = !PluginTable.isUnderstood(id) && scope.conventions.conventionFor(id) == null

    private fun declaredDependencies(
        moduleId: ModuleId,
        project: ConfiguredProject,
        sourceSets: List<SourceSet>,
    ): DeclaredDependencies {
        val anchors = FileAnchors(project.plan.directory, scope.plan.root.directory.value)
        val edges = DependencyEdges(moduleId, scope.plan.root.id, projectPaths, sourceSets, anchors)
        return project.scripts.sources.flatMap(DependencyReader::read).map { declaration ->
            declaration to edges.of(declaration, notations.parse(declaration.call, declaration.expression))
        }
    }

    private fun moduleOf(
        project: ConfiguredProject,
        config: AndroidConfig?,
        moduleId: ModuleId,
        inferred: Boolean,
        declared: DeclaredDependencies,
    ): Module {
        val processors = declared.filter { (declaration, _) -> isKspConfiguration(declaration.configuration) }
            .flatMap { (_, edges) -> edges.dependencies }
            .mapNotNull(::coordinateOf)
        val settingsRootName = scope.plan.settings?.rootName
        val rootName = settingsRootName.takeIf { project.plan.path == ":" }
        val group = ProjectGroup(project.plan.path, settingsRootName)
        val attributes = StaticModuleAttributes.of(project.ids, config, project.scripts.sources, processors) +
            listOfNotNull(rootName?.let { "gradle.rootProject.name" to it }) +
            ComposeResourceReader.attributes(project.ids, project.scripts.sources, group) +
            project.outputAttributes
        val heuristic = project.plan.heuristic || inferred || project.scripts.sources.any { it.heuristic }
        val buildLogic = ModuleTrait.BUILD_LOGIC.takeIf { scope.plan.included }
        return Module(
            id = moduleId,
            buildRoot = scope.plan.root.id,
            path = project.plan.path,
            directory = RepoPath(project.plan.directory),
            plugins = project.ids.toList(),
            traits = ModuleTraits.ofPlugins(project.ids) + listOfNotNull(buildLogic),
            androidNamespace = config?.namespace,
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = if (heuristic) Certainty.HEURISTIC else Certainty.EXACT,
            attributes = attributes.toSortedMap(),
        )
    }

    private fun isKspConfiguration(configuration: String) =
        configuration.startsWith("ksp") && !configuration.endsWith("Classpath")

    private fun coordinateOf(edge: Relationship): String? {
        val target = (edge.resolution as? Resolution.Resolved)?.target as? NodeRef.OfExternal
        return target?.coordinate?.removePrefix("maven:")
    }
}
