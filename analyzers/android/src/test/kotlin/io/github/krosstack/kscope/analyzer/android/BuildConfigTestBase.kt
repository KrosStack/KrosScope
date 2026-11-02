package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.analyzer.android.buildconfig.BuildConfigSynthesizer
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.SyntheticSource

internal val appBuildConfig = mapOf(
    "android.buildFeatures.buildConfig" to "true",
    "android.buildConfigField.SYNC_ENDPOINT" to "String",
    "android.buildConfigField.MAX_TASKS" to "int",
    "android.buildConfigField.VERBOSE_LOGGING" to "boolean",
)

internal val libBuildConfig = mapOf("android.buildFeatures.buildConfig" to "true")

internal class BuildConfigResult(val facts: List<Fact>, val sources: List<SyntheticSource>) {
    fun source(namespace: String): SyntheticSource =
        sources.single { it.virtualPath == "android-buildconfig/${namespace.replace('.', '/')}/BuildConfig.java" }

    fun symbols(): List<Symbol> = facts.filterIsInstance<Fact.OfSymbol>().map { it.value }

    fun generates(): List<Relationship> =
        facts.filterIsInstance<Fact.OfRelationship>().map { it.value }.filter { it.kind.name == "GENERATES" }

    fun flavorFields(namespace: String): List<String> = fieldNames(namespace).filter { "FLAVOR" in it }

    fun fieldNames(namespace: String): List<String> = symbols()
        .filter { it.kind.id == "java.field" && it.qualifiedName?.startsWith("$namespace.BuildConfig.") == true }
        .map { it.name }
}

/** What the build config tests share: the synthesizer and a way to run it over a model of the `ui` tree. */
internal open class BuildConfigTestBase {
    protected val synthesizer = BuildConfigSynthesizer()

    protected fun run(
        appAttributes: Map<String, String> = appBuildConfig,
        flavors: List<String> = emptyList(),
        libAttributes: Map<String, String> = emptyMap(),
        overrides: Map<String, String> = emptyMap(),
    ): BuildConfigResult {
        val model = DroidModel.standard(
            appAttributes = appAttributes,
            flavors = flavors,
            libAttributes = libAttributes,
        )
        val (batches, sources) = SampleTree("ui", model = model, overrides = overrides).synthesizeModules(synthesizer)
        return BuildConfigResult(batches.facts(), sources)
    }
}
