package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.spi.worker.ScriptCall
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FunctionInliningOutlineTest {
    private val fixture = OutlineFixture()
    private val library = fixture.ofSources("plugins/convention").values.flatMap { it.pluginClasses }
        .single { it.name == "acme.LibraryConventionPlugin" }
    private val configure = library.apply.single { it.name == "configure" }

    @AfterTest
    fun closeFixture() = fixture.close()

    private fun ScriptCall.child(name: String) = body.single { it.name == name }

    @Test
    fun `a helper function is inlined at its call with its parameters declared`() {
        val helper = configure.child("configureAndroidBase")
        assertTrue(helper.inlined)
        val bindings = helper.body.takeWhile { it.name == "val" }
        assertEquals(listOf("this", "commonExtension"), bindings.map { it.literals().first() })
        assertEquals(listOf("Project"), bindings[0].typeArguments)
        assertEquals(listOf("CommonExtension"), bindings[1].typeArguments)
        assertEquals("this", bindings[1].arguments[1].expression)
        assertEquals(mapOf("compileSdk" to "35"), helper.child("apply").assignments)
    }

    @Test
    fun `inlined bodies are placed at the call and type parameters are replaced`() {
        val helper = configure.child("configureAndroidBase")
        assertTrue(helper.body.all { it.range == helper.range })
        val kotlin = helper.child("configureKotlin")
        assertTrue(kotlin.inlined)
        assertEquals(listOf("KotlinAndroidProjectExtension"), kotlin.child("configure").typeArguments)
    }

    @Test
    fun `functions beyond the depth limit stay calls and are reported`() {
        assertEquals(listOf("jvmTargetFor"), library.notInlined)
        val unread = configure.child("configureAndroidBase").child("configureKotlin").child("configure")
        assertFalse(unread.child("jvmTargetFor").inlined)
    }
}
