package io.github.krosstack.kscope.analyzer.jni.libraries

import kotlin.test.Test
import kotlin.test.assertEquals

class CMakeBracketBoundaryTest {
    private fun names(text: String) = CMakeLibraryReader().read(text).map { it.name }

    @Test
    fun `brackets inside an unquoted argument open no bracket argument and hide no later comment`() {
        val text = "message(foo[[)\n# add_library(fake SHARED x.c)\nadd_library(real SHARED a.c)"
        assertEquals(listOf("real"), names(text))
    }

    @Test
    fun `an unclosed bracket pair inside an unquoted argument leaves a commented library out`() {
        assertEquals(emptyList(), names("message(foo[[)\n# add_library(fake SHARED x.c)"))
    }

    @Test
    fun `a closed bracket pair inside an unquoted argument is plain text`() {
        val text = "message(foo[[x]])\nadd_library(real SHARED a.c)"
        assertEquals(listOf("real"), names(text))
    }

    @Test
    fun `an escaped space does not end the unquoted argument that a bracket follows`() {
        val text = "message(a\\ [[)\n# add_library(fake SHARED x.c)\nadd_library(real SHARED a.c)"
        assertEquals(listOf("real"), names(text))
    }

    @Test
    fun `a bracket argument and a bracket comment at the start of their tokens still work`() {
        val text = """
            message([[ ( ]])
            #[[ add_library(fake2 SHARED y.c) ]]
            add_library(real SHARED a.c)
        """.trimIndent()
        assertEquals(listOf("real"), names(text))
    }
}
