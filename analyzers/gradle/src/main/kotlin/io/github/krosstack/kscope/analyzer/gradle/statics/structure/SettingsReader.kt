package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.script.allCalls
import io.github.krosstack.kscope.analyzer.gradle.script.child
import io.github.krosstack.kscope.analyzer.gradle.script.firstExpression
import io.github.krosstack.kscope.analyzer.gradle.script.firstLiteral
import io.github.krosstack.kscope.analyzer.gradle.script.positional
import io.github.krosstack.kscope.analyzer.gradle.statics.StaticDiagnostics
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.spi.worker.ScriptCall

internal class ProjectInclude(val path: String)

internal class CatalogImport(val name: String, val file: String)

/**
 * What a settings script declares: the name of the root project, the projects that are included, the directories
 * given to them, the builds that are included, and the catalogs that are imported. [problems] are the declarations
 * that are computed, and [hasDynamicIncludes] says that some project may be missing from [includes].
 */
internal data class SettingsFacts(
    val rootName: String?,
    val includes: List<ProjectInclude>,
    val projectDirs: Map<String, String>,
    val includedBuilds: List<String>,
    val catalogImports: List<CatalogImport>,
    val hasDynamicIncludes: Boolean,
    val problems: List<Diagnostic>,
)

private val projectDirTarget = Regex("""project\(\s*["'](:[^"']+)["']\s*\)\.projectDir""")
private const val BASE_DIRECTORY = """(?:rootDir|settingsDir|rootProject\.projectDir)"""
private val fileExpression =
    Regex("""(?:new\s+)?(?:file|File)\(\s*(?:$BASE_DIRECTORY\s*,\s*)?["']([^"'$]+)["']\s*\)""")
private val filesExpression = Regex("""files\(\s*["']([^"']+)["']\s*\)""")
private val catalogCreators = setOf("create", "register", "maybeCreate")

internal class SettingsReader(private val artifact: ArtifactRef) {
    fun read(calls: List<ScriptCall>): SettingsFacts {
        val all = calls.allCalls().filter { it.receiver == null || it.receiver == "settings" }
        val includes = all.filter { it.name == "include" }
        val builds = all.filter { it.name == "includeBuild" }
        val dirs = projectDirs(calls.allCalls())
        val rootName = calls.allCalls().firstOrNull { it.name == "=" && it.receiver == "rootProject.name" }
        val computed = (includes + builds).filter { call -> call.positional().any { it.literal == null } }
        return SettingsFacts(
            rootName = rootName?.firstLiteral(),
            includes = includes.flatMap { call -> call.positional().mapNotNull { it.literal } }.map(::projectPath),
            projectDirs = dirs.directories,
            includedBuilds = builds.mapNotNull { it.firstLiteral() },
            catalogImports = catalogImports(all),
            hasDynamicIncludes = computed.isNotEmpty() || all.any { it.name == "includeFlat" },
            problems = computed.map { StaticDiagnostics.dynamicInclude(it.name, location(it)) } + dirs.problems,
        )
    }

    private class ProjectDirs(val directories: Map<String, String>, val problems: List<Diagnostic>)

    private fun projectDirs(calls: List<ScriptCall>): ProjectDirs {
        val assignments = calls.filter { it.name == "=" }.mapNotNull { call ->
            val path = call.receiver?.let { projectDirTarget.matchEntire(it) }?.groupValues?.get(1)
            path?.let { it to call }
        }
        val directories = assignments.mapNotNull { (path, call) ->
            call.firstExpression()?.let { fileExpression.matchEntire(it.trim()) }?.let { path to it.groupValues[1] }
        }.toMap()
        val problems = assignments.filter { (path, _) -> path !in directories }
            .map { (path, call) -> StaticDiagnostics.dynamicProjectDir(path, location(call)) }
        return ProjectDirs(directories, problems)
    }

    private fun catalogImports(calls: List<ScriptCall>): List<CatalogImport> =
        calls.filter { it.name == "versionCatalogs" }.flatMap { it.body }.mapNotNull { catalog ->
            val name = if (catalog.name in catalogCreators) catalog.firstLiteral() else catalog.name
            val file = catalog.child("from")?.firstExpression()?.let { filesExpression.find(it) }?.groupValues?.get(1)
            if (name != null && file != null) CatalogImport(name, file) else null
        }

    private fun projectPath(path: String): ProjectInclude = ProjectInclude(":" + path.trim(':'))

    private fun location(call: ScriptCall) = SourceLocation(artifact, call.range)
}
