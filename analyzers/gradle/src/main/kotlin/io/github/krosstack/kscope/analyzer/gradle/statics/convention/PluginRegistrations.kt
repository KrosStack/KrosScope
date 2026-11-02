package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogLookup
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogResolver
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogSection
import io.github.krosstack.kscope.analyzer.gradle.script.blocks
import io.github.krosstack.kscope.analyzer.gradle.script.child
import io.github.krosstack.kscope.analyzer.gradle.script.setting
import io.github.krosstack.kscope.analyzer.gradle.script.unquote
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptOutlines
import io.github.krosstack.kscope.analyzer.gradle.statics.android.NamedObjects
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.BuildPlan
import io.github.krosstack.kscope.analyzer.gradle.statics.structure.ProjectPlan
import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.spi.worker.ScriptCall
import java.io.StringReader
import java.util.Properties

/** A plugin that a build registers: the id it is applied by, its implementation class, and its project. */
internal class PluginRegistration(val id: String, val implementationClass: String, val projectDirectory: String)

private const val DESCRIPTORS = "src/main/resources/META-INF/gradle-plugins"
private const val DESCRIPTOR_SUFFIX = ".properties"
private const val IMPLEMENTATION_KEY = "implementation-class"
private val creators = setOf("create", "register")
private val registrationContainers = setOf("gradlePlugin.plugins", "plugins")

/**
 * Reads which plugin ids an included build registers and with which classes: the `gradlePlugin { plugins { } }` block
 * of the build scripts of its projects, with ids given as strings or as entries of a version catalog, and the plugin
 * descriptors `META-INF/gradle-plugins/<id>.properties` of their resources. A descriptor that cannot be parsed is
 * left out and reported in [problems].
 */
internal class PluginRegistrationReader(private val outlines: ScriptOutlines, private val disk: WorkspaceDisk) {
    private val unreadable = mutableListOf<Diagnostic>()

    val problems: List<Diagnostic> get() = unreadable.toList()

    fun read(plan: BuildPlan, catalogs: CatalogResolver): List<PluginRegistration> =
        plan.projects.flatMap { project -> fromScript(project, catalogs) + fromDescriptors(project) }

    private fun fromScript(project: ProjectPlan, catalogs: CatalogResolver): List<PluginRegistration> {
        val calls = project.script?.let { outlines.sourceOf(it) }?.calls.orEmpty()
        val declared = calls.blocks("gradlePlugin").flatMap { NamedObjects.entries(it.child("plugins")) } +
            calls.filter { it.name in creators && it.receiver in registrationContainers }.mapNotNull(::unnamed)
        return declared.mapNotNull { (_, call) -> registrationOf(call, catalogs, project.directory) }
    }

    private fun unnamed(call: ScriptCall): Pair<String, ScriptCall>? =
        call.arguments.firstOrNull()?.literal?.let { it to call }

    private fun registrationOf(call: ScriptCall, catalogs: CatalogResolver, directory: String): PluginRegistration? {
        val id = call.setting("id")?.let { idOf(it, catalogs) }
        val implementation = call.setting("implementationClass")?.let(::unquote)
        return if (id != null && implementation != null) PluginRegistration(id, implementation, directory) else null
    }

    private fun idOf(raw: String, catalogs: CatalogResolver): String? {
        val lookup = catalogs.lookup(raw) as? CatalogLookup.Found
        val entry = lookup?.reference?.entry?.takeIf { it.section == CatalogSection.PLUGINS }
        return unquote(raw) ?: entry?.value
    }

    private fun fromDescriptors(project: ProjectPlan): List<PluginRegistration> {
        val directory = RepoPaths.join(project.directory, DESCRIPTORS)
        return disk.children(directory).filter { it.endsWith(DESCRIPTOR_SUFFIX) }.mapNotNull { name ->
            val path = RepoPaths.join(directory, name)
            val implementation = disk.readText(path)?.let { implementationOf(path, it) }
            implementation?.let { PluginRegistration(name.removeSuffix(DESCRIPTOR_SUFFIX), it, project.directory) }
        }
    }

    private fun implementationOf(path: String, text: String): String? {
        val properties = try {
            Properties().apply { load(StringReader(text)) }
        } catch (malformed: IllegalArgumentException) {
            unreadable += ConventionDiagnostics.descriptorUnreadable(path, malformed.message)
            null
        }
        return properties?.getProperty(IMPLEMENTATION_KEY)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
