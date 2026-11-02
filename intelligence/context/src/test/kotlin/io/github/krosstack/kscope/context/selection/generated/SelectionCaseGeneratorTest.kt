package io.github.krosstack.kscope.context.selection.generated

import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SelectionCaseGeneratorTest {
    @Test
    fun `a seed always gives the same case`() {
        val first = SelectionCaseGenerator(SAMPLE_SEED).generate()
        val second = SelectionCaseGenerator(SAMPLE_SEED).generate()

        assertEquals(first.input, second.input)
        assertEquals(first.graph.artifacts, second.graph.artifacts)
        assertEquals(first.graph.symbols, second.graph.symbols)
        assertEquals(first.graph.edges, second.graph.edges)
        assertEquals(first.graph.structure, second.graph.structure)
        assertEquals(
            first.offers(ContextLevel.DEEP, ESTIMATOR),
            second.offers(ContextLevel.DEEP, ESTIMATOR),
        )
    }

    @Test
    fun `the first three hundred and sixty seeds are ordinary and later seeds cycle through the shaped profiles`() {
        assertEquals(setOf(CaseProfile.ORDINARY), (1L..ORDINARY_SEEDS).map(::caseProfileOf).toSet())
        assertEquals(
            listOf(CaseProfile.DENSE, CaseProfile.TWINS, CaseProfile.TIES, CaseProfile.DENSE),
            (ORDINARY_SEEDS + 1..ORDINARY_SEEDS + 4).map(::caseProfileOf),
        )
    }

    @Test
    fun `the cases cover the workspaces and offers that the selection is sensitive to`() {
        val facts = SEEDS.map { caseFactsOf(SelectionCaseGenerator(it).generate(), ESTIMATOR) }

        assertAtLeast("one repository", facts.count { it.repositories == 1 })
        assertAtLeast("two repositories", facts.count { it.repositories == 2 })
        assertAtLeast("three repositories", facts.count { it.repositories == 3 })
        assertAtLeast("modules out of path order", facts.count { it.modulesOutOfPathOrder })
        assertAtLeast("equal module and path", facts.count { it.sameModuleAndPath }, minimum = 10)
        assertAtLeast("equal node keys", facts.count { it.sameNodeKey }, minimum = 4)
        assertAtLeast("lone surrogates", facts.count { it.loneSurrogates })
        assertAtLeast("fewer than ten unresolved", facts.count { it.unresolved in 1..9 })
        assertAtLeast("ten to ninety nine unresolved", facts.count { it.unresolved in 10..99 })
        assertAtLeast("a hundred or more unresolved", facts.count { it.unresolved >= 100 })
        assertAtLeast("offers of three variants", facts.count { it.threeVariants })
        assertAtLeast("duplicated offers", facts.count { it.duplicatedOffers })
        assertAtLeast("generated surfaces", facts.count { it.generatedSurface })
        assertAtLeast("test roles", facts.count { it.testRole })
        assertAtLeast("merged fragments", facts.count { it.mergedFragments })
        assertAtLeast("a budget that binds", facts.count { it.bindingBudget })
        assertAtLeast("eight or more offers in one file", facts.count { it.manyOffersInFile })
        assertAtLeast("a single-variant and a longer offer in one file", facts.count { it.exhaustedEarly })
        assertAtLeast("files with equal offers", facts.count { it.twinFiles })
        assertAtLeast("offers without value", facts.count { it.zeroValue })
        assertAtLeast("offers without value and equal node keys", facts.count { it.zeroValueKeyCollision })
        assertAtLeast("a whole file that absorbed declarations", facts.count { it.absorbedFile })
        println("generator-coverage offers per case: max=${facts.maxOf { it.offers }} mean=${meanOffers(facts)}")
    }

    private fun meanOffers(facts: List<CaseFacts>): Int = facts.sumOf { it.offers } / facts.size

    private fun assertAtLeast(what: String, actual: Int, minimum: Int = MINIMUM) {
        println("generator-coverage $what=$actual of ${SEEDS.count()}")
        assertTrue(actual >= minimum, "$what: $actual of ${SEEDS.count()} cases, expected at least $minimum")
    }

    private companion object {
        val SEEDS = 1L..720L
        val ESTIMATOR = TokenEstimators.create(TokenizerId("chars_4"))
        const val SAMPLE_SEED = 17L
        const val ORDINARY_SEEDS = 360L
        const val MINIMUM = 20
    }
}
