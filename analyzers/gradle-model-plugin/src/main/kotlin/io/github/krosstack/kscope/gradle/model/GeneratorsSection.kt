package io.github.krosstack.kscope.gradle.model

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

/**
 * What code generator plugins were configured to produce: SQLDelight databases, Wire output, KSP processors and the
 * resource class of Compose Multiplatform.
 */
internal class GeneratorsSection(private val project: Project, private val errors: SectionErrors) {
    fun describe(): Map<String, Any?> = linkedMapOf(
        "sqldelightDatabases" to errors.guard("generators", "sqldelight") { sqldelightDatabases() }.orEmpty(),
        "wireKotlin" to errors.guard("generators", "wire") { wireGeneratesKotlin() },
        "kspProcessors" to errors.guard("generators", "ksp") { kspProcessors() }.orEmpty(),
        "composeResources" to errors.guard("generators", "compose") { composeResources() },
    )

    private fun composeResources(): Map<String, Any?>? {
        val compose = project.extensions.findByName("compose") as? ExtensionAware
        val resources = compose?.extensions?.findByName("resources") ?: return null
        return linkedMapOf(
            "packageOfResClass" to Values.text(Reflect.get(resources, "packageOfResClass"))?.ifBlank { null },
            "publicResClass" to (Values.plain(Reflect.get(resources, "publicResClass")) as? Boolean),
            "group" to project.group.toString(),
        )
    }

    private fun sqldelightDatabases(): List<Map<String, Any?>> {
        val extension = project.extensions.findByName("sqldelight") ?: return emptyList()
        return Values.items(Reflect.get(extension, "databases")).map { database ->
            linkedMapOf(
                "name" to Values.text(Reflect.get(database, "name")),
                "packageName" to Values.text(Reflect.get(database, "packageName")),
            )
        }.sortedBy { it["name"].toString() }
    }

    private fun wireGeneratesKotlin(): Boolean? {
        val extension = project.extensions.findByName("wire") ?: return null
        return WIRE_TARGET_PROPERTIES.firstNotNullOfOrNull { property ->
            Reflect.get(extension, property)?.let { targets -> Values.items(targets).any(::isKotlinTarget) }
        }
    }

    private fun isKotlinTarget(target: Any?): Boolean = target != null && "Kotlin" in target.javaClass.simpleName

    private fun kspProcessors(): List<String> = project.configurations
        .filter { it.name.startsWith("ksp") && !it.name.endsWith("Classpath") }
        .flatMap { configuration -> configuration.dependencies.map { "${it.group}:${it.name}" } }
        .distinct()
        .sorted()

    private companion object {
        val WIRE_TARGET_PROPERTIES = listOf("kotlinTargets", "targets", "outputs")
    }
}
