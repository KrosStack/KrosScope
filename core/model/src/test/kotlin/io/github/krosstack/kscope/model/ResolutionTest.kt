package io.github.krosstack.kscope.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ResolutionTest {
    @Test
    fun `every resolution state can be handled without a fallback branch`() {
        assertEquals(listOf("resolved", "unresolved", "not analysed"), Samples.resolutions.map(::describe))
    }

    @Test
    fun `an unresolved target and an unanalysed target are different states`() {
        val reason = ReasonCode("kotlin.unresolved-reference")
        assertNotEquals<Resolution>(Resolution.Unresolved("x", reason), Resolution.NotAnalysed("x", reason))
    }

    private fun describe(resolution: Resolution): String = when (resolution) {
        is Resolution.Resolved -> "resolved"
        is Resolution.Unresolved -> "unresolved"
        is Resolution.NotAnalysed -> "not analysed"
    }
}
