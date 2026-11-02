package io.github.krosstack.kscope.analyzer.gradle.statics.android

import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogLookup
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogResolver
import io.github.krosstack.kscope.analyzer.gradle.catalog.CatalogSection
import io.github.krosstack.kscope.analyzer.gradle.script.blocks
import io.github.krosstack.kscope.analyzer.gradle.script.bodies
import io.github.krosstack.kscope.analyzer.gradle.script.child
import io.github.krosstack.kscope.analyzer.gradle.script.flagSetting
import io.github.krosstack.kscope.analyzer.gradle.script.setting
import io.github.krosstack.kscope.analyzer.gradle.script.stringSetting
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptDiagnostics
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.spi.worker.ScriptCall

internal class Flavor(val name: String, val dimension: String?)

/** What the `android` blocks of a project declare, merged over the scripts that configure it. */
internal data class AndroidConfig(
    val namespace: String?,
    val compileSdk: String?,
    val features: Set<String>,
    val buildTypes: List<String>,
    val dimensions: List<String>,
    val flavors: List<Flavor>,
    val hasComputedFlavors: Boolean,
    val buildConfigFields: Map<String, String>,
    val problems: List<Diagnostic>,
)

private val booleanFeatures = listOf("viewBinding", "aidl", "buildConfig", "compose")
private val kotlinAndroidBlocks = setOf("android", "androidLibrary")
private val extensionReceivers = setOf(null, "extensions")
private val releaseSdk = Regex("""release\(\s*(\d+)[^)]*\)""")

/**
 * Reads the Android configuration of a project from the `android { }` block, the `android { }` block inside
 * `kotlin { }` of the Android Gradle plugin for Kotlin Multiplatform libraries, and, in a convention plugin, the
 * `extensions.configure<...> { }` block that configures the Android extension. Later scripts win over earlier ones.
 */
internal class AndroidReader(private val catalogs: CatalogResolver) {
    private class Block(val source: ScriptSource, val call: ScriptCall)

    fun read(sources: List<ScriptSource>): AndroidConfig {
        val blocks = sources.flatMap { source -> androidBlocks(source).map { Block(source, it) } }
        val calls = blocks.map { it.call }
        val sdks = blocks.map(::sdkOf)
        val namespaces = blocks.map(::namespaceOf)
        return AndroidConfig(
            namespace = namespaces.mapNotNull { it.first }.lastOrNull(),
            compileSdk = sdks.mapNotNull { it.first }.lastOrNull(),
            features = enabledFeatures(calls),
            buildTypes = AndroidDeclarations.buildTypes(calls),
            dimensions = AndroidDeclarations.dimensions(calls),
            flavors = AndroidDeclarations.flavors(calls),
            hasComputedFlavors = AndroidDeclarations.hasComputedFlavors(calls),
            buildConfigFields = AndroidDeclarations.buildConfigFields(calls),
            problems = (sdks + namespaces).mapNotNull { it.second },
        )
    }

    private fun androidBlocks(source: ScriptSource): List<ScriptCall> {
        val inKotlin = source.calls.bodies("kotlin").filter { it.receiver == null && it.name in kotlinAndroidBlocks }
        val configured = source.calls.filter { it.name == "configure" && it.receiver in extensionReceivers }
        return source.calls.blocks("android") + inKotlin + configured.filter { it.body.isNotEmpty() }
    }

    private fun namespaceOf(block: Block): Pair<String?, Diagnostic?> {
        val raw = block.call.setting("namespace") ?: return null to null
        val value = block.call.stringSetting("namespace")
        return value to if (value == null) problem("namespace", raw, block) else null
    }

    private fun sdkOf(block: Block): Pair<String?, Diagnostic?> {
        val raw = block.call.setting("compileSdk") ?: block.call.setting("compileSdkVersion")
            ?: block.call.child("compileSdk")?.setting("version") ?: return null to null
        val value = sdkValue(raw)
        return value to if (value == null) problem("compileSdk", raw, block) else null
    }

    private fun sdkValue(raw: String): String? {
        val text = raw.trim().trim('"', '\'').removePrefix("android-")
        val release = releaseSdk.matchEntire(raw.trim())?.groupValues?.get(1)
        val entry = (catalogs.lookup(raw) as? CatalogLookup.Found)?.reference?.entry
        val fromCatalog = entry?.takeIf { it.section == CatalogSection.VERSIONS }?.value
        return listOfNotNull(text.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }, release, fromCatalog)
            .firstOrNull { value -> value.all(Char::isDigit) }
    }

    private fun problem(property: String, raw: String, block: Block) =
        ScriptDiagnostics.valueNotAnalysed(property, raw, block.source.location(block.call))

    private fun enabledFeatures(blocks: List<ScriptCall>): Set<String> =
        booleanFeatures.filter { feature -> blocks.mapNotNull { flagOf(it, feature) }.lastOrNull() == true }.toSet()

    // `buildFeatures { viewBinding = true }` and the dotted `buildFeatures.viewBinding = true` of the `android` block.
    private fun flagOf(block: ScriptCall, feature: String): Boolean? =
        block.child("buildFeatures")?.flagSetting(feature) ?: block.flagSetting("buildFeatures.$feature")
}
