package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.openapi.client.ClientPackages

private const val GENERATOR_NAME = "generatorName"
private const val PACKAGE_NAME = "packageName"
private const val API_PACKAGE = "apiPackage"
private const val MODEL_PACKAGE = "modelPackage"
private const val DEFAULT_PACKAGE = "org.openapitools.client"

/**
 * The settings of an `openApiGenerate` block that decide which Kotlin client the generator makes. A setting that the
 * block gives but not as a literal is in [values] with no value, and a setting that it does not give is not.
 */
internal class OpenApiSettings(private val values: Map<String, String?>) {
    val generatorName: String? get() = values[GENERATOR_NAME]

    /** The packages of the client, with the defaults of the generator; null when one is set to something computed. */
    fun packages(): ClientPackages? {
        val base = setting(PACKAGE_NAME) { DEFAULT_PACKAGE }
        val api = setting(API_PACKAGE) { base?.let { "$it.apis" } }
        val model = setting(MODEL_PACKAGE) { base?.let { "$it.models" } }
        return if (api != null && model != null) ClientPackages(api, model) else null
    }

    private fun setting(name: String, default: () -> String?): String? = if (name in values) values[name] else default()

    companion object {
        const val INPUT_SPEC = "inputSpec"
        val names = setOf(GENERATOR_NAME, INPUT_SPEC, PACKAGE_NAME, API_PACKAGE, MODEL_PACKAGE)
    }
}
