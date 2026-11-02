package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.ProviderInputs
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptOutlines
import io.github.krosstack.kscope.analyzer.gradle.statics.StaticDiagnostics
import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId

/** A project of a build: its Gradle path, its directory, its build script if it has one. */
internal class ProjectPlan(val path: String, val directory: String, val script: Artifact?, val heuristic: Boolean)

/** A build: the settings it was read from (none for an included build without a settings script) and its projects. */
internal class BuildPlan(
    val root: BuildRoot,
    val settings: SettingsFacts?,
    val included: Boolean,
    val projects: List<ProjectPlan>,
)

private const val BUILD_SRC = "buildSrc"

internal class BuildStructure(val plans: List<BuildPlan>, val problems: List<Diagnostic>) {
    val hasSettings: Boolean get() = plans.any { it.settings != null }

    /**
     * The builds whose build logic [plan] can reach: the ones it includes and its `buildSrc`, and in turn the ones
     * these include, since a plugin applies the plugins on its own classpath.
     */
    fun includedBy(plan: BuildPlan): List<BuildPlan> {
        val reached = mutableListOf<BuildPlan>()
        val pending = ArrayDeque(listOf(plan))
        while (pending.isNotEmpty()) {
            val next = directlyIncludedBy(pending.removeFirst()).filter { it !in reached }
            reached += next
            pending += next
        }
        return reached
    }

    private fun directlyIncludedBy(plan: BuildPlan): List<BuildPlan> {
        val directory = plan.root.directory.value
        val declared = plan.settings?.includedBuilds.orEmpty().mapNotNull { RepoPaths.resolve(directory, it) }
        val included = declared.toSet() + RepoPaths.join(directory, BUILD_SRC)
        return plans.filter { it.included && it.root.directory.value in included }
    }
}

/**
 * Finds the builds of a workspace and the projects of each. Every directory with a settings script is a build, as is
 * every directory that is included by another build or is `buildSrc`. A settings script that computes its includes
 * adds the projects whose directories hold a build script.
 */
internal class BuildStructureReader(
    private val repository: RepositoryId,
    private val inputs: ProviderInputs,
    private val outlines: ScriptOutlines,
) {
    private val problems = mutableListOf<Diagnostic>()

    fun read(): BuildStructure {
        val facts = inputs.settings.mapNotNull(::factsOf).toMap()
        val included = includedDirectories(facts)
        val roots = (inputs.settings.map(inputs::directoryOf) + included).toSortedSet()
        val plans = roots.map { planOf(it, facts[it], it in included, roots) }
        return BuildStructure(plans, problems + plans.flatMap { it.settings?.problems.orEmpty() })
    }

    private fun factsOf(settings: Artifact): Pair<String, SettingsFacts>? {
        val source = outlines.sourceOf(settings)
        if (source == null) problems += StaticDiagnostics.scriptNotAnalysed(settings)
        return source?.let { inputs.directoryOf(settings) to SettingsReader(settings.ref).read(it.calls) }
    }

    private fun includedDirectories(facts: Map<String, SettingsFacts>): Set<String> {
        val included = sortedSetOf<String>()
        val pending = ArrayDeque(facts.keys + inputs.settings.map(inputs::directoryOf))
        val visited = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val directory = pending.removeFirst()
            if (!visited.add(directory)) continue
            val declared = facts[directory]?.includedBuilds.orEmpty().map { it to RepoPaths.resolve(directory, it) }
            problems += declared.filter { it.second == null }.map { StaticDiagnostics.buildOutsideWorkspace(it.first) }
            val found = declared.mapNotNull { it.second } + listOfNotNull(buildSrcOf(directory))
            included += found
            pending += found
        }
        return included
    }

    private fun buildSrcOf(directory: String): String? =
        RepoPaths.join(directory, BUILD_SRC).takeIf { inputs.scriptIn(it) != null }

    private fun planOf(directory: String, settings: SettingsFacts?, included: Boolean, roots: Set<String>): BuildPlan {
        val id = BuildRootId("${repository.value}//$directory")
        val root = BuildRoot(id, repository, RepoPath(directory), BuildSystem.GRADLE)
        val rootProject = ProjectPlan(":", directory, inputs.scriptIn(directory), false)
        val paths = settings?.includes.orEmpty().flatMap { withParents(it.path) }.distinct()
        val declared = paths.mapNotNull { projectOf(it, directory, settings) }
        val discovered = if (settings?.hasDynamicIncludes == true) discover(directory, roots, declared) else emptyList()
        return BuildPlan(root, settings, included, listOf(rootProject) + declared + discovered)
    }

    private fun withParents(path: String): List<String> {
        val segments = path.trim(':').split(':')
        return segments.indices.map { ":" + segments.take(it + 1).joinToString(":") }
    }

    private fun projectOf(path: String, root: String, settings: SettingsFacts?): ProjectPlan? {
        val custom = settings?.projectDirs?.get(path)?.let { RepoPaths.resolve(root, it) }
        val directory = custom ?: RepoPaths.directoryOf(root, path)
        return ProjectPlan(path, directory, inputs.scriptIn(directory), false)
    }

    private fun discover(root: String, roots: Set<String>, declared: List<ProjectPlan>): List<ProjectPlan> {
        val known = declared.map { it.directory }.toSet() + root
        return inputs.buildScripts.map(inputs::directoryOf).distinct()
            .filter { it !in known && RepoPaths.isWithin(it, root) && nearestRoot(it, roots) == root }
            .map { directory ->
                val relative = directory.removePrefix(root).trim('/')
                ProjectPlan(":" + relative.replace('/', ':'), directory, inputs.scriptIn(directory), true)
            }
    }

    private fun nearestRoot(directory: String, roots: Set<String>): String? =
        generateSequence(directory) { if (it.isEmpty()) null else RepoPaths.parentOf(it) }.firstOrNull { it in roots }
}
