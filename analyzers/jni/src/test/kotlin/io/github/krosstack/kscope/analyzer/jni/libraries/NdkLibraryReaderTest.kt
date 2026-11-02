package io.github.krosstack.kscope.analyzer.jni.libraries

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class NdkLibraryReaderTest {
    private fun read(text: String) = NdkLibraryReader().read(text.trimIndent())

    private fun names(text: String) = read(text).map { it.name }

    @Test
    fun `the module of a block that ends in the shared library include is a library`() {
        val library = read(
            """
            LOCAL_PATH := ${'$'}(call my-dir)

            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE    := tasktide_checksum
            LOCAL_SRC_FILES := checksum.c
            include ${'$'}(BUILD_SHARED_LIBRARY)
            """,
        ).single()
        assertEquals("tasktide_checksum", library.name)
        assertEquals("ndk.library", library.kind)
        assertEquals("shared", library.linkage)
        assertEquals(Certainty.EXACT, library.certainty)
        assertEquals(TextRange(4, 20, 4, 37), library.nameRange)
        assertEquals(TextRange(4, 1, 4, 37), library.declaration)
    }

    @Test
    fun `static libraries, executables and prebuilt modules are not shared libraries of the file`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := a
            include ${'$'}(BUILD_STATIC_LIBRARY)

            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := b
            include ${'$'}(BUILD_EXECUTABLE)

            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := c
            include ${'$'}(PREBUILT_SHARED_LIBRARY)

            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := d
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(listOf("d"), names(text))
    }

    @Test
    fun `assignment operators, braces, comments and continued lines are read`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            # LOCAL_MODULE := commented
            LOCAL_MODULE:=first # trailing comment
            include ${'$'}{BUILD_SHARED_LIBRARY}

            include ${'$'}(CLEAR_VARS)
            LOCAL_SRC_FILES := a.c \
                b.c
            LOCAL_MODULE ?= second
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(listOf("first", "second"), names(text))
    }

    @Test
    fun `a module name made of variables is not a name`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := ${'$'}(MODULE_NAME)
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(emptyList(), names(text))
    }

    @Test
    fun `an include without a module before it gives nothing and does not carry the last module over`() {
        val text = """
            include ${'$'}(BUILD_SHARED_LIBRARY)
            LOCAL_MODULE := late
            include ${'$'}(CLEAR_VARS)
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(emptyList(), names(text))
    }
}
