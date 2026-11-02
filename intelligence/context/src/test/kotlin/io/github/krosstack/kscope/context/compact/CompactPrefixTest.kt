package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.context.compact.CompactWorkspace.Companion.LINE
import io.github.krosstack.kscope.context.compact.CompactWorkspace.Companion.big
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRendering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class CompactPrefixTest {
    @Test
    fun `the remainder is filled with an exact line prefix of the best skipped file`() {
        val workspace = CompactWorkspace(big(), "fun small() = 1\n")

        val result = workspace.compile(workspace.input(workspace.file(0), workspace.file(1)), 700)

        val prefix = result.fragments.single { it.artifact.path.value == "source/File0" }
        assertTrue(prefix.text.isNotEmpty() && workspace.text(0).startsWith(prefix.text))
        assertTrue(prefix.text.length < workspace.text(0).length)
        assertTrue(prefix.text.lines().all { it.length == LINE })
        assertEquals(TextRange(1, 1, prefix.text.lines().size, LINE + 1), prefix.range)
        assertTrue(result.totalTokens.value in (700 - MAXIMUM_SLACK)..700)
        assertTrue(result.omitted.none { it.reason == ContextOmissionReason.BUDGET })
    }

    @Test
    fun `a skipped file that is already represented gets no prefix`() {
        val workspace = CompactWorkspace(big())
        val symbol = declaration("head", workspace.artifact(0), TextRange(1, 1, 1, LINE + 1), "fun head()")
        val input = ContextInput(
            "t",
            listOf(
                ContextCandidate(NodeRef.OfArtifact(workspace.file(0)), 1.0),
                ContextCandidate(NodeRef.OfSymbol(symbol.id), 0.5),
            ),
        )

        val result = workspace.compile(input, 700, listOf(symbol))

        assertEquals(listOf("a".repeat(LINE)), result.fragments.map { it.text })
    }

    @Test
    fun `a skipped declaration is cut at line boundaries from its own start`() {
        val workspace = CompactWorkspace("package sample\n\n" + big())
        val range = TextRange(3, 1, 63, 1)
        val symbol = declaration("body", workspace.artifact(0), range, "fun body()")
        val input = ContextInput("t", listOf(ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0)))

        val result = workspace.compile(input, 700, listOf(symbol))

        val prefix = result.fragments.single()
        assertEquals(ContextRendering.DECLARATION, prefix.rendering)
        assertEquals(3, prefix.range?.startLine)
        assertTrue(workspace.text(0).substring("package sample\n\n".length).startsWith(prefix.text))
        assertTrue(prefix.text.length < big().length)
    }

    private companion object {
        const val MAXIMUM_SLACK = 60
    }
}
