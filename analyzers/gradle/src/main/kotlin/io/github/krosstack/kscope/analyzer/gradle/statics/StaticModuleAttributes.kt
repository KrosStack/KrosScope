package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.script.blocks
import io.github.krosstack.kscope.analyzer.gradle.script.child
import io.github.krosstack.kscope.analyzer.gradle.script.stringSetting
import io.github.krosstack.kscope.analyzer.gradle.statics.android.AndroidConfig
import io.github.krosstack.kscope.analyzer.gradle.statics.android.NamedObjects

private val kotlinPlugins = mapOf(
    "org.jetbrains.kotlin.plugin.serialization" to "kotlin.plugin.serialization",
    "org.jetbrains.kotlin.plugin.parcelize" to "kotlin.plugin.parcelize",
    "org.jetbrains.kotlin.plugin.compose" to "kotlin.plugin.compose",
)

/** The build facts later phases read from a module, keyed as documented for `Module.attributes`. */
internal object StaticModuleAttributes {
    fun of(
        plugins: Set<String>,
        android: AndroidConfig?,
        sources: List<ScriptSource>,
        kspProcessors: List<String>,
    ): Map<String, String> =
        (androidAttributes(android) + pluginAttributes(plugins) + generatorAttributes(sources, kspProcessors))
            .toSortedMap()

    private fun androidAttributes(android: AndroidConfig?): Map<String, String> {
        if (android == null) return emptyMap()
        val sdk = listOfNotNull(android.compileSdk?.let { "android.compileSdk" to it })
        val features = android.features.map { "android.buildFeatures.$it" to "true" }
        val fields = android.buildConfigFields.map { (name, type) -> "android.buildConfigField.$name" to type }
        val dimensions = android.dimensions.takeIf { it.isNotEmpty() }
            ?.let { "android.flavorDimensions" to it.joinToString(",") }
        return (sdk + features + fields + listOfNotNull(dimensions)).toMap()
    }

    private fun pluginAttributes(plugins: Set<String>): Map<String, String> =
        plugins.mapNotNull { id -> kotlinPlugins[id]?.let { it to "true" } }.toMap()

    private fun generatorAttributes(sources: List<ScriptSource>, kspProcessors: List<String>): Map<String, String> {
        val databases = sources.flatMap { source ->
            source.calls.blocks("sqldelight").flatMap { NamedObjects.entries(it.child("databases")) }
        }.mapNotNull { (name, call) ->
            call.stringSetting("packageName")?.let { "sqldelight.database.$name.package" to it }
        }
        val wireBlocks = sources.flatMap { it.calls.blocks("wire") }
        val generatesKotlin = wireBlocks.any { block -> block.body.any { it.name == "kotlin" } }
        val wire = ("wire.kotlin" to "true").takeIf { generatesKotlin }
        val ksp = kspProcessors.distinct().sorted().joinToString(",").takeIf { it.isNotEmpty() }
        return (databases + listOfNotNull(wire, ksp?.let { "ksp.processors" to it })).toMap()
    }
}
