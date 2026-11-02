package io.github.krosstack.kscope.analyzer.jni.libraries

import kotlin.test.Test
import kotlin.test.assertEquals

class NdkLibraryReaderAssignmentTest {
    private fun names(text: String) = NdkLibraryReader().read(text.trimIndent()).map { it.name }

    @Test
    fun `a literal module name overwritten by a variable expression gives no library`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := base
            LOCAL_MODULE := ${'$'}(LOCAL_MODULE)_jni
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(emptyList(), names(text))
    }

    @Test
    fun `a later literal assignment replaces an earlier one`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := a
            LOCAL_MODULE = b
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(listOf("b"), names(text))
    }

    @Test
    fun `an assignment only if unset keeps an earlier value, resolved or not`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := a
            LOCAL_MODULE ?= b
            include ${'$'}(BUILD_SHARED_LIBRARY)

            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := ${'$'}(NAME)
            LOCAL_MODULE ?= c
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(listOf("a"), names(text))
    }

    @Test
    fun `an appended value makes the module name two words and so no library`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := a
            LOCAL_MODULE += x
            include ${'$'}(BUILD_SHARED_LIBRARY)

            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE += y
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(listOf("y"), names(text))
    }

    @Test
    fun `a value of several words is not a name and replaces an earlier module`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := a
            LOCAL_MODULE := b                 c
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(emptyList(), names(text))
    }

    @Test
    fun `clearing the variables starts the next block without the module of the previous one`() {
        val text = """
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE := ${'$'}(NAME)
            include ${'$'}(CLEAR_VARS)
            LOCAL_MODULE ?= fresh
            include ${'$'}(BUILD_SHARED_LIBRARY)
        """
        assertEquals(listOf("fresh"), names(text))
    }
}
