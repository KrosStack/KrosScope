package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.GeneratorsDocument
import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument

/** The build facts later phases read from a module, keyed as documented for `Module.attributes`. */
internal object ModuleAttributes {
    private val enabledBuildFeatures = setOf("viewBinding", "aidl", "buildConfig", "compose")

    private val kotlinPlugins = mapOf(
        "org.jetbrains.kotlin.plugin.serialization" to "kotlin.plugin.serialization",
        "org.jetbrains.kotlin.plugin.parcelize" to "kotlin.plugin.parcelize",
        "org.jetbrains.kotlin.plugin.compose" to "kotlin.plugin.compose",
    )

    fun of(document: ProjectDocument): Map<String, String> {
        val facts = androidAttributes(document) + pluginAttributes(document) + generatorAttributes(document)
        val outputs = PublicationAttributes.of(document.publication) + ArchiveAttributes.of(document.archives)
        return (facts + outputs).toSortedMap()
    }

    private fun androidAttributes(document: ProjectDocument): Map<String, String> {
        val android = document.android ?: return emptyMap()
        val sdk = listOfNotNull(android.compileSdk?.let { "android.compileSdk" to it })
        val features = android.buildFeatures.filter { it in enabledBuildFeatures }
            .map { "android.buildFeatures.$it" to "true" }
        val fields = android.variants.flatMap { it.buildConfigFields.entries }
            .mapNotNull { (name, type) -> type?.let { "android.buildConfigField.$name" to it } }
        val dimensions = android.flavorDimensions.takeIf { it.isNotEmpty() }
            ?.let { "android.flavorDimensions" to it.joinToString(",") }
        return (sdk + features + fields + listOfNotNull(dimensions)).toMap()
    }

    private fun pluginAttributes(document: ProjectDocument): Map<String, String> =
        document.plugins.mapNotNull { id -> kotlinPlugins[id]?.let { it to "true" } }.toMap()

    private fun generatorAttributes(document: ProjectDocument): Map<String, String> {
        val generators = document.generators
        val databases = generators.sqldelightDatabases.mapNotNull { database ->
            database.packageName?.let { "sqldelight.database.${database.name}.package" to it }
        }
        val wire = listOfNotNull(("wire.kotlin" to "true").takeIf { generators.wireKotlin == true })
        val ksp = listOfNotNull(
            ("ksp.processors" to generators.kspProcessors.sorted().joinToString(",")).takeIf {
                generators.kspProcessors.isNotEmpty()
            },
        )
        return (databases + wire + ksp).toMap() + composeAttributes(generators)
    }

    private fun composeAttributes(generators: GeneratorsDocument): Map<String, String> {
        val compose = generators.composeResources ?: return emptyMap()
        return ComposeResourceAttributes.of(compose.packageOfResClass, compose.publicResClass, compose.group)
    }
}
