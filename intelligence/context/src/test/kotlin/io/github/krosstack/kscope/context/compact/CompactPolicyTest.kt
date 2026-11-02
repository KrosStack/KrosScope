package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.context.CharacterEstimator
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.IdentityWorkspace
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CompactPolicyTest {
    private val estimator = CharacterEstimator()
    private val workspace = IdentityWorkspace()

    @Test
    fun `compact packages hold raw source only and no map signatures outlines or summaries`() {
        ContextLevel.entries.map { compile(it, COMPACT_BUDGET) }.forEach { compact ->
            assertTrue(compact.fragments.isNotEmpty())
            assertTrue(compact.fragments.all { it.sourceBacked && it.rendering in RAW_RENDERINGS })
            assertTrue(compact.fragments.none { it.role == ContextRole.BOUNDARY })
            assertEquals(emptyList(), compact.boundaries + compact.tests + compact.unresolved)
            listOf("[signature]", "[outline]", "[generated]", "Unresolved relationships").forEach {
                assertFalse(compact.markdown.contains(it), it)
            }
        }
    }

    @Test
    fun `compact packages start with the task line and respect the exact markdown budget`() {
        listOf(1, 200, 800, 2_000, 4_000, 7_999).forEach { budget ->
            val result = compile(ContextLevel.RECOMMENDED, budget)

            assertTrue(result.totalTokens.value <= budget, "budget=$budget")
            assertEquals(estimator.count(result.markdown), result.totalTokens)
            assertTrue(result.markdown.isEmpty() || result.markdown.startsWith("Task: ${workspace.input.task}\n\n"))
        }
    }

    @Test
    fun `generated candidates are omitted instead of summarised`() {
        val surface = ContextCandidate(NodeRef.OfSymbol(workspace.generatedSurface.id), 1.0)
        val input = workspace.input.copy(candidates = listOf(surface))

        val result = compiler().compile(input, ContextLevel.RECOMMENDED, TokenCount(COMPACT_BUDGET))

        assertTrue(result.fragments.isEmpty())
        assertEquals(listOf(ContextOmissionReason.GENERATED_BODY), result.omitted.map { it.reason })
    }

    @Test
    fun `compact packages are deterministic across compilers`() {
        assertEquals(compile(ContextLevel.DEEP, COMPACT_BUDGET), compile(ContextLevel.DEEP, COMPACT_BUDGET))
    }

    @Test
    fun `the compact policy applies strictly below eight thousand tokens`() {
        val compact = compile(ContextLevel.RECOMMENDED, 7_999)
        val legacy = compile(ContextLevel.RECOMMENDED, 8_000)

        assertTrue(compact.fragments.all { it.rendering in RAW_RENDERINGS })
        assertTrue(legacy.fragments.any { it.rendering !in RAW_RENDERINGS })
    }

    @Test
    fun `the legacy policy remains reachable for any budget through the threshold`() {
        val legacy = ContextCompiler(workspace.fixture, workspace.fixture, estimator, TokenCount(0))

        val result = legacy.compile(workspace.input, ContextLevel.RECOMMENDED, TokenCount(COMPACT_BUDGET))

        assertTrue(result.fragments.any { it.rendering !in RAW_RENDERINGS })
    }

    private fun compiler(): ContextCompiler = ContextCompiler(workspace.fixture, workspace.fixture, estimator)

    private fun compile(level: ContextLevel, budget: Int): ContextPackage =
        compiler().compile(workspace.input, level, TokenCount(budget))

    private companion object {
        const val COMPACT_BUDGET = 2_000
        val RAW_RENDERINGS = setOf(ContextRendering.FILE, ContextRendering.DECLARATION, ContextRendering.ENTRY)
    }
}
