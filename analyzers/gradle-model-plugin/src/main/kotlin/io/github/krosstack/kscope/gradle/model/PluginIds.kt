package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.net.URL
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * Resolves the ids of the plugins applied to a project, including those applied transitively by other plugins. A
 * plugin class is mapped back to its ids through the `META-INF/gradle-plugins` descriptors of the jar it came from.
 */
internal object PluginIds {
    private const val DESCRIPTOR_DIRECTORY = "META-INF/gradle-plugins/"
    private const val DESCRIPTOR_SUFFIX = ".properties"
    private const val IMPLEMENTATION_KEY = "implementation-class"
    private const val DECORATED_SUFFIX = "_Decorated"

    private const val CORE_PREFIX = "org.gradle."
    private const val INTERNAL_SEGMENT = ".internal."

    private val appliedAsPlumbing = setOf(
        "base",
        "build-init",
        "help-tasks",
        "java-base",
        "jvm-ecosystem",
        "jvm-test-suite",
        "jvm-toolchains",
        "lifecycle-base",
        "reporting-base",
        "software-reporting-tasks",
        "test-report-aggregation",
        "test-suite-base",
        "wrapper",
        "org.gradle.kotlin.kotlin-dsl-project",
        "org.jetbrains.compose.hot-reload",
        "org.jetbrains.kotlin.plugin.scripting",
    )

    private val classesByJar = ConcurrentHashMap<String, Map<String, List<String>>>()

    fun appliedIds(project: Project): List<String> = project.plugins
        .flatMap { idsUsedInBuildScripts(project, idsOf(it)) }
        .filter { it !in appliedAsPlumbing }
        .distinct()
        .sorted()

    private fun idsOf(plugin: Plugin<*>): List<String> {
        val location = plugin.javaClass.protectionDomain?.codeSource?.location ?: return emptyList()
        val idsByClass = classesByJar.getOrPut(location.toString()) { readDescriptors(location) }
        return idsByClass[plugin.javaClass.name.removeSuffix(DECORATED_SUFFIX)].orEmpty()
    }

    // An internal id such as com.android.internal.application stands for the public com.android.application, and a
    // core plugin is known without its org.gradle. prefix.
    private fun idsUsedInBuildScripts(project: Project, ids: List<String>): List<String> {
        val resolved = ids.mapNotNull { id ->
            when {
                INTERNAL_SEGMENT in id -> publicIdOf(project, id)
                isCorePlugin(id) -> id.removePrefix(CORE_PREFIX)
                else -> id
            }
        }
        val qualified = resolved.filter { '.' in it }
        return qualified.ifEmpty { resolved }
    }

    private fun publicIdOf(project: Project, internalId: String): String? =
        internalId.replace(INTERNAL_SEGMENT, ".").takeIf { project.pluginManager.hasPlugin(it) }

    private fun isCorePlugin(id: String) = id.startsWith(CORE_PREFIX) && '.' !in id.removePrefix(CORE_PREFIX)

    private fun readDescriptors(location: URL): Map<String, List<String>> {
        val file = File(location.toURI())
        if (!file.isFile) return emptyMap()
        return JarFile(file).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.startsWith(DESCRIPTOR_DIRECTORY) && it.name.endsWith(DESCRIPTOR_SUFFIX) }
                .mapNotNull { entry ->
                    val properties = Properties().apply { jar.getInputStream(entry).use(::load) }
                    properties.getProperty(IMPLEMENTATION_KEY)?.let { implementation ->
                        implementation to entry.name.removePrefix(DESCRIPTOR_DIRECTORY).removeSuffix(DESCRIPTOR_SUFFIX)
                    }
                }
                .groupBy({ it.first }, { it.second })
        }
    }
}
