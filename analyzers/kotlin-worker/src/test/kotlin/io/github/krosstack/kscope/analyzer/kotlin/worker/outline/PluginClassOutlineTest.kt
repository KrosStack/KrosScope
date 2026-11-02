package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.spi.worker.PluginClassOutline
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NESTED = "acme.AppConventionPlugin\$Nested"

class PluginClassOutlineTest {
    private val fixture = OutlineFixture()
    private val outlines = fixture.ofSources("plugins/convention")

    @AfterTest
    fun closeFixture() = fixture.close()

    private fun classes(): List<PluginClassOutline> = outlines.values.flatMap { it.pluginClasses }

    private fun pluginClass(name: String) = classes().single { it.name == name }

    @Test
    fun `the source outline of a file lists its plugin classes and no calls`() {
        assertEquals(emptyList(), outlines.getValue("src/main/kotlin/acme/LibraryConventionPlugin.kt").calls)
        val names = classes().map { it.name }.sorted()
        assertEquals(listOf("acme.AppConventionPlugin", NESTED, "acme.LibraryConventionPlugin"), names)
    }

    @Test
    fun `a class that does not implement Plugin is not listed`() {
        assertTrue(classes().none { it.name.endsWith("NotAPlugin") })
    }

    @Test
    fun `the apply parameter is declared first and with is unwrapped`() {
        val apply = pluginClass("acme.LibraryConventionPlugin").apply
        assertEquals(listOf("val", "apply", "apply", "configure", "dependencies"), apply.map { it.name })
        assertEquals(listOf("Project"), apply.first().typeArguments)
        assertEquals(listOf("target"), apply.first().literals())
        assertEquals("com.android.library", apply[1].arguments.single().literal)
        assertEquals("plugin", apply[1].arguments.single().name)
    }

    @Test
    fun `type arguments of the configured extension are kept`() {
        val configure = pluginClass("acme.LibraryConventionPlugin").apply.single { it.name == "configure" }
        assertEquals("extensions", configure.receiver)
        assertEquals(listOf("LibraryExtension"), configure.typeArguments)
        assertEquals(mapOf("defaultConfig.targetSdk" to "35", "resourcePrefix" to "\"lib_\""), configure.assignments)
    }

    @Test
    fun `conditionals and loops are calls and not unconditional statements`() {
        val apply = pluginClass("acme.AppConventionPlugin").apply
        val expected = listOf("val", "val", "configureAndroidBase", "apply", "if", "forEach", "val", "dependencies")
        assertEquals(expected, apply.map { it.name })
        assertEquals(listOf("apply"), apply.single { it.name == "if" }.body.map { it.name })
    }

    @Test
    fun `a local keeps its declared or initialising call`() {
        val local = pluginClass("acme.AppConventionPlugin").apply[1]
        assertEquals("android", local.literals().first())
        assertEquals(listOf("ApplicationExtension"), local.body.single().typeArguments)
        assertEquals("extensions", local.body.single().receiver)
    }

    @Test
    fun `apply with a type argument keeps the plugin class`() {
        val apply = pluginClass("acme.AppConventionPlugin").apply
        val applyOther = apply.single { it.name == "apply" && it.typeArguments.isNotEmpty() }
        assertEquals(listOf("LibraryConventionPlugin"), applyOther.typeArguments)
    }

    @Test
    fun `a nested class and an expression body are read`() {
        val nested = pluginClass(NESTED).apply
        assertEquals(listOf("val", "apply"), nested.map { it.name })
        assertEquals("pluginManager", nested[1].receiver.orEmpty().substringAfterLast('.'))
    }
}
