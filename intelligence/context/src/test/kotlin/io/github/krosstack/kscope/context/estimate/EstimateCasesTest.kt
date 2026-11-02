package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class EstimateCasesTest {
    private val estimator = TokenEstimators.create(TokenizerId("chars_4"))

    @Test
    fun `the evidence case holds a note on a block that is persisted and on one that is not`() {
        val fragments = compiled("evidence notes", ContextLevel.RECOMMENDED).fragments

        assertTrue(fragments.any { it.evidenceNote != null && it.rendering in EstimateRuns.persisted })
        assertTrue(fragments.any { it.evidenceNote != null && it.rendering !in EstimateRuns.persisted })
    }

    @Test
    fun `the range case merges adjacent declarations and keeps a distant one apart`() {
        val fragments = compiled("adjacent ranges that merge", ContextLevel.RECOMMENDED).fragments

        assertTrue(fragments.any { it.node is NodeRef.OfArtifact && it.rendering == ContextRendering.DECLARATION })
        assertTrue(fragments.any { it.node is NodeRef.OfSymbol && it.rendering == ContextRendering.DECLARATION })
    }

    @Test
    fun `the file case lets a whole file absorb its declarations`() {
        val fragments = compiled("a file that absorbs its declarations", ContextLevel.RECOMMENDED).fragments

        assertEquals(listOf(ContextRendering.FILE), fragments.map { it.rendering })
    }

    @Test
    fun `the generated case holds a generated summary and the source it comes from at the full level`() {
        val renderings = { level: ContextLevel ->
            compiled("a generated summary", level).fragments.map { it.rendering }
        }

        assertTrue(ContextRendering.GENERATED in renderings(ContextLevel.RECOMMENDED))
        assertTrue(ContextRendering.GENERATED in renderings(ContextLevel.FULL))
        assertTrue(ContextRendering.FILE in renderings(ContextLevel.FULL))
    }

    @Test
    fun `the test cases hold an outline until the deep level and a neighbour only at the deep level`() {
        val outline = { level: ContextLevel -> compiled("a test outline", level).fragments.map { it.rendering } }
        val reached = { level: ContextLevel ->
            compiled("a call neighbour", level).fragments.any { it.artifact.path.value == "source/Reached" }
        }

        assertTrue(ContextRendering.OUTLINE in outline(ContextLevel.RECOMMENDED))
        assertTrue(ContextRendering.OUTLINE !in outline(ContextLevel.DEEP))
        assertTrue(reached(ContextLevel.DEEP))
        assertTrue(!reached(ContextLevel.RECOMMENDED))
    }

    @Test
    fun `the omission cases leave out a stale file a binary file and a node without source`() {
        val reasons = { name: String -> compiled(name, ContextLevel.FULL).omitted.map { it.reason } }

        assertTrue(ContextOmissionReason.STALE_INDEX in reasons("a stale file"))
        assertTrue(ContextOmissionReason.BINARY in reasons("binary content as a file"))
        assertTrue(ContextOmissionReason.NO_SOURCE in reasons("candidates without source"))
    }

    @Test
    fun `the unresolved case counts the relationships of the selected files only`() {
        val name = "unresolved relationships inside and outside"

        assertEquals(3, compiled(name, ContextLevel.RECOMMENDED).unresolved.size)
        assertEquals(4, compiled(name, ContextLevel.FULL).unresolved.size)
        assertTrue(compiled(name, ContextLevel.FULL).markdown.contains("Unresolved relationships: 4\n"))
    }

    @Test
    fun `the empty cases give an empty package and a package of one task line`() {
        val empty = compiled("an empty input", ContextLevel.FULL)
        val task = compiled("only a task", ContextLevel.FULL)

        assertEquals("", empty.markdown)
        assertEquals(0, empty.totalTokens.value)
        assertTrue(task.markdown.startsWith("Task: ") && task.fragments.isEmpty())
        assertNotEquals(0, task.totalTokens.value)
    }

    private fun compiled(name: String, level: ContextLevel): ContextPackage {
        val case = estimateCase(name)
        return ContextCompiler(case.fixture, case.fixture, estimator).compile(case.input, level)
    }
}
