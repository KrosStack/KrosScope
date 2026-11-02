package io.github.krosstack.kscope.benchmark.agent.workspace

import io.github.krosstack.kscope.benchmark.agent.Arm
import io.github.krosstack.kscope.context.TokenEstimators
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextPreparerTest {
    private val estimator = TokenEstimators.create()
    private val statement = "Add a reminder scheduler so that tasks can notify people before they are due."

    @TempDir
    lateinit var temporary: Path

    private fun workspace(): Path {
        val root = temporary.resolve("workspace")
        Files.createDirectories(root.resolve("core"))
        val scheduler = "package sample\n\nclass ReminderScheduler {\n    fun schedule(at: Long) = at > 0\n}\n"
        val unrelated = "package sample\n\nclass Unrelated {\n    val colour = \"blue\"\n}\n"
        Files.writeString(root.resolve("core/ReminderScheduler.kt"), scheduler)
        Files.writeString(root.resolve("core/Unrelated.kt"), unrelated)
        Files.writeString(root.resolve("README.md"), "# Sample\n\nA tiny project for tests.\n")
        return root
    }

    private fun index(): Path = Files.createDirectories(temporary.resolve("index"))

    @Test
    fun `arm A carries no context`() {
        val prepared = ContextPreparer(2000, estimator).prepare(Arm.A, statement, workspace(), index(), emptySet())

        assertNull(prepared.markdown)
        assertEquals(0, prepared.record.tokens)
        assertEquals(2000, prepared.record.budget)
    }

    @Test
    fun `arm B packs whole files by BM25 rank within the budget and counts its tokens`() {
        val gold = setOf("core/ReminderScheduler.kt")

        val prepared = ContextPreparer(2000, estimator).prepare(Arm.B, statement, workspace(), index(), gold)

        val markdown = assertNotNull(prepared.markdown)
        assertTrue(markdown.contains("class ReminderScheduler"))
        assertTrue(prepared.record.tokens in 1..2000, prepared.record.tokens.toString())
        assertEquals(estimator.count(markdown).value, prepared.record.tokens)
        assertTrue("core/ReminderScheduler.kt" in prepared.record.files)
        assertEquals(1, prepared.record.goldFilesInContext)
        assertEquals("o200k_base", prepared.record.estimator)
    }

    @Test
    fun `arm C compiles the KrosScope context within the same budget and records its latency`() {
        val prepared = ContextPreparer(2000, estimator).prepare(Arm.C, statement, workspace(), index(), emptySet())

        val markdown = assertNotNull(prepared.markdown)
        assertTrue(markdown.isNotBlank())
        assertTrue(prepared.record.tokens in 1..2000, prepared.record.tokens.toString())
        assertEquals(estimator.count(markdown).value, prepared.record.tokens)
        assertTrue(prepared.record.indexMs >= 0 && prepared.record.contextMs >= 0)
    }

    @Test
    fun `the prompt is identical for every arm apart from the context block`() {
        val plain = PromptTemplate.render("  The statement.  ", null)
        val withContext = PromptTemplate.render("The statement.", "# Context body\n")

        assertTrue(withContext.startsWith(plain.trimEnd()))
        assertTrue(withContext.contains("## Context"))
        assertTrue(withContext.trimEnd().endsWith("# Context body"))
        assertTrue(plain.contains("The statement."))
        assertEquals(false, plain.contains("## Context"))
    }
}
