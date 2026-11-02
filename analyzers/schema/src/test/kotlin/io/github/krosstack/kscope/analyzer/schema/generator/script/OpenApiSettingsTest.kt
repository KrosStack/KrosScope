package io.github.krosstack.kscope.analyzer.schema.generator.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun settingsOf(script: String): OpenApiSettings? = BuildScriptReader(script).inputs().first().openApi

private fun block(vararg settings: String): String =
    "openApiGenerate {\n    inputSpec.set(\"a\")\n" + settings.joinToString("") { "    $it\n" } + "}\n"

class OpenApiSettingsTest {
    @Test
    fun `the generator settings are the literals of the block, the last of each counting`() {
        val script = """
            openApiGenerate {
                generatorName.set("kotlin-spring")
                generatorName.set("kotlin")
                inputSpec.set("a.yaml")
                packageName.set("sample.api")
                modelPackage.set("sample.api.dto")
            }
        """.trimIndent()
        val settings = checkNotNull(settingsOf(script))

        assertEquals("kotlin", settings.generatorName)
        assertEquals("sample.api.apis" to "sample.api.dto", settings.packages()?.let { it.api to it.model })
    }

    @Test
    fun `the packages of the generator are the defaults of the generator unless the block sets them`() {
        val settings = checkNotNull(settingsOf("openApiGenerate { inputSpec.set(\"a.yaml\") }"))

        assertNull(settings.generatorName)
        assertEquals(
            "org.openapitools.client.apis" to "org.openapitools.client.models",
            settings.packages()?.let { it.api to it.model },
        )
    }

    @Test
    fun `a package that the block sets to something computed leaves the packages unknown`() {
        val computed = checkNotNull(settingsOf(block("packageName.set(base)")))
        val partly = checkNotNull(settingsOf(block("apiPackage.set(base)")))
        val fixed = checkNotNull(
            settingsOf(block("packageName.set(base)", "apiPackage.set(\"p.a\")", "modelPackage.set(\"p.m\")")),
        )

        assertNull(computed.packages())
        assertNull(partly.packages())
        assertEquals("p.a" to "p.m", fixed.packages()?.let { it.api to it.model })
    }
}
