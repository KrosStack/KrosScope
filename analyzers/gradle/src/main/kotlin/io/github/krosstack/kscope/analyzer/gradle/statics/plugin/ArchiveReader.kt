package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.mapping.ArchiveAttributes
import io.github.krosstack.kscope.analyzer.gradle.mapping.ModuleTraits
import io.github.krosstack.kscope.analyzer.gradle.script.firstExpression
import io.github.krosstack.kscope.analyzer.gradle.script.setting
import io.github.krosstack.kscope.analyzer.gradle.script.unquote
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.spi.worker.ScriptCall

private const val ROOT_PATH = ":"
private const val UNSPECIFIED = "unspecified"
private const val JAR = ".jar"
private const val AAR = ".aar"
private val assignedNames = setOf("archivesBaseName", "project.archivesBaseName", "base.archivesName")
private val setNames = setOf("base.archivesName", "project.base.archivesName")
private val notPlainJvm = setOf(
    ModuleTrait.ANDROID_APPLICATION,
    ModuleTrait.ANDROID_LIBRARY,
    ModuleTrait.KOTLIN_MULTIPLATFORM,
    ModuleTrait.KOTLIN_ANDROID,
)

/**
 * The archive that a JVM module or an Android library builds, from string literals only: `<archive name>-<version>`
 * with `.jar`, or `.aar` for an Android library that is not multiplatform, and without the version when the module
 * sets none. The archive name is that of `base { archivesName }` or `archivesBaseName`, otherwise the name of the
 * project. A name or version that the scripts compute, other than from a Gradle property read by a literal key, leaves
 * the archive unknown, and nothing is recorded.
 */
internal class ArchiveReader(private val values: ProjectValues, private val rootName: String?) {
    fun read(path: String, directory: String, ids: Set<String>, sources: List<ScriptSource>): Map<String, String> {
        val extension = extensionOf(ModuleTraits.ofPlugins(ids)) ?: return emptyMap()
        val declared = archiveName(sources)
        val name = if (declared != null) declared.text else projectName(path)
        val version = values.of("version", sources, directory)
        val versionText = version?.text?.takeIf { it.isNotBlank() && it != UNSPECIFIED }
        val known = name != null && (version == null || version.text != null)
        val archive = if (known) fileName(checkNotNull(name), versionText, extension) else null
        return ArchiveAttributes.of(listOfNotNull(archive))
    }

    private fun extensionOf(traits: Set<ModuleTrait>): String? = when {
        ModuleTrait.KOTLIN_MULTIPLATFORM in traits -> null
        ModuleTrait.ANDROID_LIBRARY in traits -> AAR
        traits.any { it in notPlainJvm } -> null
        ModuleTrait.KOTLIN_JVM in traits || ModuleTrait.JAVA in traits -> JAR
        else -> null
    }

    private fun archiveName(sources: List<ScriptSource>): Part? = sources.flatMap { source ->
        source.calls.mapNotNull(::nameOf).map { Part(unquote(it), it, source.heuristic) }
    }.lastOrNull()

    private fun nameOf(call: ScriptCall): String? = when {
        call.name == "base" && call.receiver == null -> call.setting("archivesName")
        call.name == "=" && call.receiver in assignedNames -> call.arguments.firstOrNull()?.expression
        call.name == "set" && call.receiver in setNames -> call.firstExpression()
        else -> null
    }

    private fun projectName(path: String): String? =
        (if (path == ROOT_PATH) rootName else path.substringAfterLast(':'))?.takeIf { it.isNotEmpty() }

    private fun fileName(name: String, version: String?, extension: String): String =
        if (version == null) "$name$extension" else "$name-$version$extension"
}
