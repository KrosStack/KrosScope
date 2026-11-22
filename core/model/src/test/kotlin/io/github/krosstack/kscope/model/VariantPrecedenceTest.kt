package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals

class VariantPrecedenceTest {
    @Test
    fun `a two dimension variant ranks itself, its build type, the flavor combination, each flavor, then main`() {
        val variant = variant("freeDemoDebug", "debug", listOf("free", "demo"))
        val expected = listOf("freeDemoDebug", "debug", "freeDemo", "free", "demo", "main")

        assertEquals(expected, variant.sourceSetPrecedence())
    }

    @Test
    fun `a single flavor appears once`() {
        val variant = variant("fullRelease", "release", listOf("full"))

        assertEquals(listOf("fullRelease", "release", "full", "main"), variant.sourceSetPrecedence())
    }

    @Test
    fun `a variant without flavors names no flavor combination`() {
        assertEquals(listOf("debug", "main"), variant("debug", "debug", emptyList()).sourceSetPrecedence())
    }

    @Test
    fun `a variant without a build type skips it`() {
        assertEquals(listOf("free", "main"), variant("free", null, listOf("free")).sourceSetPrecedence())
    }

    private fun variant(name: String, buildType: String?, flavors: List<String>) =
        Variant(VariantId("app:$name"), ModuleId("app"), name, buildType, flavors)
}
