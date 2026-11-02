package io.github.krosstack.kscope.context.compact

import io.github.krosstack.kscope.context.compact.CompactWorkspace.Companion.LINE
import io.github.krosstack.kscope.context.compact.CompactWorkspace.Companion.big
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class CompactPackingTest {
    @Test
    fun `the whole file is packed when the ranking points at a fitting file`() {
        val workspace = CompactWorkspace(("a".repeat(LINE) + "\n").repeat(3))

        val result = workspace.compile(workspace.input(workspace.file(0)), 1_000)

        val fragment = result.fragments.single()
        assertEquals(ContextRendering.FILE, fragment.rendering)
        assertEquals(workspace.text(0), fragment.text)
        assertEquals(TextRange(1, 1, 4, 1), fragment.range)
    }

    @Test
    fun `ranked declarations are packed without the rest of their file`() {
        val workspace = CompactWorkspace("fun wanted() = 1\n", "fun other() = 2\n")
        val symbol = declaration("wanted", workspace.artifact(0), TextRange(1, 1, 1, 17), "fun wanted()")
        val input = ContextInput("t", listOf(ContextCandidate(NodeRef.OfSymbol(symbol.id), 1.0)))

        val result = workspace.compiler(listOf(symbol)).compile(input, ContextLevel.RECOMMENDED, TokenCount(1_000))

        assertEquals(listOf("fun wanted() = 1"), result.fragments.map { it.text })
        assertEquals(ContextRendering.DECLARATION, result.fragments.single().rendering)
    }

    @Test
    fun `a declaration inside an already packed file adds nothing`() {
        val workspace = CompactWorkspace("fun first() = 1\nfun second() = 2\n")
        val symbol = declaration("first", workspace.artifact(0), TextRange(1, 1, 1, 16), "fun first()")
        val input = ContextInput(
            "t",
            listOf(
                ContextCandidate(NodeRef.OfArtifact(workspace.file(0)), 1.0),
                ContextCandidate(NodeRef.OfSymbol(symbol.id), 0.5),
            ),
        )

        val result = workspace.compile(input, 1_000, listOf(symbol))

        assertEquals(listOf(workspace.text(0)), result.fragments.map { it.text })
    }

    @Test
    fun `what does not fit is skipped and lower ranked files still fit in rank order`() {
        val small = "fun small() = 1\n"
        val other = "fun other() = 2\n"
        val workspace = CompactWorkspace(big(), small, other)

        val result = workspace.compile(workspace.input(workspace.file(0), workspace.file(1), workspace.file(2)), 700)

        val texts = result.fragments.associate { it.artifact.path.value to it.text }
        assertEquals(small, texts.getValue("source/File1"))
        assertEquals(other, texts.getValue("source/File2"))
        assertNotEquals(workspace.text(0), texts["source/File0"])
    }

    @Test
    fun `candidates that cannot be packed are reported with their reason`() {
        val workspace = CompactWorkspace(big(), "fun small() = 1\n")

        val result = workspace.compile(workspace.input(workspace.file(1), workspace.file(0)), 50)

        assertTrue(result.fragments.isEmpty())
        assertEquals(
            listOf(
                NodeRef.OfArtifact(workspace.file(0)) to ContextOmissionReason.BUDGET,
                NodeRef.OfArtifact(workspace.file(1)) to ContextOmissionReason.BUDGET,
            ),
            result.omitted.map { it.node to it.reason },
        )
    }

    @Test
    fun `ranked test files are left out below the deep level and reported`() {
        val workspace = CompactWorkspace("fun main() = 1\n", "fun check() = 2\n")

        val result = workspace.compile(testInput(workspace), 1_000, level = ContextLevel.RECOMMENDED)

        assertEquals(listOf(workspace.text(0)), result.fragments.map { it.text })
        assertEquals(
            listOf(NodeRef.OfArtifact(workspace.file(1)) to ContextOmissionReason.LEVEL_LIMIT),
            result.omitted.map { it.node to it.reason },
        )
    }

    @Test
    fun `ranked test files are packed as raw source in rank order at the deep level`() {
        val workspace = CompactWorkspace("fun main() = 1\n", "fun check() = 2\n")

        val result = workspace.compile(testInput(workspace), 1_000, level = ContextLevel.DEEP)

        assertEquals(listOf(workspace.text(0), workspace.text(1)), result.fragments.map { it.text })
        assertEquals(ContextRole.TEST, result.fragments.last().role)
    }

    @Test
    fun `a budget below the task header yields an empty package that reports the budget`() {
        val workspace = CompactWorkspace("fun main() = 1\n")

        val result = workspace.compile(workspace.input(workspace.file(0)), 3)

        assertTrue(result.fragments.isEmpty())
        assertEquals("", result.markdown)
        assertTrue(result.omitted.any { it.node == null && it.reason == ContextOmissionReason.BUDGET })
    }

    private fun testInput(workspace: CompactWorkspace): ContextInput = ContextInput(
        "t",
        listOf(
            ContextCandidate(NodeRef.OfArtifact(workspace.file(1)), 1.0, ContextRole.TEST),
            ContextCandidate(NodeRef.OfArtifact(workspace.file(0)), 0.5),
        ),
        tests = listOf(NodeRef.OfArtifact(workspace.file(1))),
    )
}
