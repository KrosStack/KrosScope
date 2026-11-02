package io.github.krosstack.kscope.gradle.model

import io.github.krosstack.kscope.gradle.model.publication.ArchivesSection
import io.github.krosstack.kscope.gradle.model.publication.PublicationSection
import org.gradle.api.Project

/** Assembles the document of one project. Each section is guarded, so a failing section is empty and reported. */
internal class ProjectDocument(private val project: Project, state: KscopeProjectState) {
    private val errors = state.errors
    private val kotlin = project.extensions.findByName("kotlin")
    private val androidDsl = project.extensions.findByName("android")
    private val androidSection = AndroidSection(project, state)
    private val isMultiplatform = kotlin != null && Reflect.has(kotlin, "targets")

    // A plain Android module has its source sets from the Android section, which the Kotlin ones only repeat.
    private val hasOwnKotlinSourceSets = androidDsl == null || isMultiplatform

    fun build(): Map<String, Any?> {
        val android = android()
        val classpathStarted = System.nanoTime()
        val classpaths = errors.guard("classpaths") { classpaths(android) }
        val classpathMillis = (System.nanoTime() - classpathStarted) / NANOS_PER_MILLI
        return linkedMapOf(
            "schema" to SCHEMA_VERSION,
            "path" to project.path,
            "name" to project.name,
            "dir" to Values.slashPath(project.projectDir),
            "buildDir" to Values.slashPath(project.layout.buildDirectory.get().asFile),
            "build" to BuildInfo.describe(project),
            "plugins" to errors.guard("plugins") { PluginIds.appliedIds(project) }.orEmpty(),
            "kotlin" to kotlin?.let { errors.guard("kotlin") { KotlinSection.describe(it, hasOwnKotlinSourceSets) } },
            "android" to android,
            "java" to errors.guard("java") { JavaSection.describe(project) },
            "dependencies" to errors.guard("dependencies") { DependenciesSection.describe(project) },
            "archives" to errors.guard("archives") { ArchivesSection.describe(project) }.orEmpty(),
            "generators" to GeneratorsSection(project, errors).describe(),
            "publication" to errors.guard("publication") { PublicationSection.describe(project) },
            "catalogs" to catalogs(),
            "classpaths" to classpaths,
            "classpathMillis" to classpathMillis,
            "errors" to errors.asMap(),
        )
    }

    private fun android(): Map<String, Any?>? = errors.guard("android") {
        val target = kotlin?.let(::libraryTargetOf)
        when {
            androidDsl != null -> androidSection.describeDsl(androidDsl)
            target != null -> androidSection.describeLibraryTarget(target)
            else -> null
        }
    }

    private fun libraryTargetOf(extension: Any): Any? = KotlinSection.targets(extension)
        .firstOrNull { Values.text(Reflect.get(it, "platformType")) == ANDROID_PLATFORM }

    private fun catalogs(): List<Map<String, Any?>>? =
        if (project == project.rootProject) errors.guard("catalogs") { CatalogSection.describe(project) } else null

    private fun classpaths(android: Map<String, Any?>?): Map<String, Any?> {
        val section = ClasspathSection(project, errors)
        val boot = errors.guard("classpaths", "bootClasspath") { androidSection.bootClasspath() }.orEmpty()
        if (!isMultiplatform) section.addJavaSourceSets()
        if (kotlin != null && isMultiplatform) section.addKotlinCompilations(KotlinSection.targets(kotlin), boot)
        val variants = (android?.get("variants") as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>()
        section.addAndroidVariants(variants, boot)
        return section.describe()
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val ANDROID_PLATFORM = "androidJvm"
    }
}
