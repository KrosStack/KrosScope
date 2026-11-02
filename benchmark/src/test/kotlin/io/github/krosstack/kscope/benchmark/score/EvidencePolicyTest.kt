package io.github.krosstack.kscope.benchmark.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidencePolicyTest {
    @Test
    fun `generator output is satisfied by a convention and nothing else is relaxed`() {
        assertTrue(EvidencePolicy.satisfies("generated-artifact", "generated-artifact"))
        assertTrue(EvidencePolicy.satisfies("generated-artifact", "convention"))
        assertFalse(EvidencePolicy.satisfies("convention", "generated-artifact"))
        assertFalse(EvidencePolicy.satisfies("generated-artifact", "textual"))
        assertFalse(EvidencePolicy.satisfies("build-model", "textual"))
        assertFalse(EvidencePolicy.satisfies("compiler", "convention"))
        assertTrue(EvidencePolicy.satisfies("textual", "textual"))
    }

    @Test
    fun `the outcome counts the accepted classes by the one that was produced and the rest as mismatches`() {
        val matching = Matching.of(
            expected = listOf(
                Item("a", "a", "generated-artifact"),
                Item("b", "b", "generated-artifact"),
                Item("c", "c", "generated-artifact"),
                Item("d", "d", "textual"),
                Item("e", "e", "build-model"),
            ),
            produced = listOf(
                Item("a", "a", "generated-artifact"),
                Item("b", "b", "convention"),
                Item("c", "c", "convention"),
                Item("d", "d", "convention"),
                Item("e", "e", "textual"),
            ),
        )
        val outcome = matching.evidenceOutcome()
        assertEquals(mapOf("convention" to 2), outcome.accepted)
        assertEquals(2, outcome.mismatches)
    }
}
