package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.script.allCalls
import io.github.krosstack.kscope.analyzer.gradle.script.child
import io.github.krosstack.kscope.analyzer.gradle.script.positional
import io.github.krosstack.kscope.analyzer.gradle.script.stringSetting
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

private val quotedString = Regex("""["']([^"']+)["']""")
private val dimensionCalls = setOf("flavorDimensions", "setFlavorDimensions")

/** The build types, flavor dimensions, product flavors and build config fields that `android` blocks declare. */
internal object AndroidDeclarations {
    fun buildTypes(blocks: List<ScriptCall>): List<String> =
        (listOf("debug", "release") + blocks.flatMap { NamedObjects.names(it.child("buildTypes")) }).distinct()

    fun dimensions(blocks: List<ScriptCall>): List<String> = blocks.flatMap { block ->
        val calls = block.body.filter { it.name in dimensionCalls || it.receiver == "flavorDimensions" }
        val assigned = block.assignments["flavorDimensions"]?.let(::quotedStrings).orEmpty()
        calls.flatMap { call -> call.positional().flatMap(::stringsOf) } + assigned
    }.distinct()

    fun flavors(blocks: List<ScriptCall>): List<Flavor> = blocks
        .flatMap { NamedObjects.entries(it.child("productFlavors")) }
        .map { (name, call) -> Flavor(name, call.stringSetting("dimension")) }
        .distinctBy { it.name }

    /** Whether a `productFlavors` block has statements that declare flavors under names the script computes. */
    fun hasComputedFlavors(blocks: List<ScriptCall>): Boolean =
        blocks.any { block -> block.child("productFlavors")?.body.orEmpty().any(NamedObjects::isComputed) }

    fun buildConfigFields(blocks: List<ScriptCall>): Map<String, String> = blocks
        .flatMap { it.body.allCalls() }
        .filter { it.name == "buildConfigField" }
        .mapNotNull { call ->
            val arguments = call.positional()
            val type = arguments.getOrNull(0)?.literal
            val name = arguments.getOrNull(1)?.literal
            if (type != null && name != null) name to type else null
        }.toMap()

    private fun stringsOf(argument: ScriptArgument): List<String> =
        argument.literal?.let { listOf(it) } ?: quotedStrings(argument.expression)

    private fun quotedStrings(text: String): List<String> =
        quotedString.findAll(text).map { it.groupValues[1] }.toList()
}
