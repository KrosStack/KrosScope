package io.github.krosstack.kscope.context.fragment

import com.sun.management.ThreadMXBean
import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRole
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ContextPlanTest {
    @Test
    fun `full planning of declarations in one file stays within a linear allocation budget`() {
        val file = sourceArtifact("source/Many", "fun member() = 1")
        val symbols = List(1024) { declaration("member$it", file, TextRange(1, 1, 1, 17), "fun member$it()") }
        val fixture = ContextFixture(listOf(file), symbols, emptyMap())
        val planner = ContextPlanner(ContextGraph(fixture))
        val input = ContextInput("fix", symbols.map { ContextCandidate(NodeRef.OfSymbol(it.id), 1.0) })
        val allocations = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        assumeTrue(allocations?.isThreadAllocatedMemorySupported == true, "Thread allocation monitoring is unavailable")
        val counter = checkNotNull(allocations)
        val wasEnabled = counter.isThreadAllocatedMemoryEnabled
        counter.isThreadAllocatedMemoryEnabled = true
        try {
            val thread = Thread.currentThread().id
            val before = counter.getThreadAllocatedBytes(thread)

            val plan = planner.plan(input, ContextLevel.FULL)

            val allocated = counter.getThreadAllocatedBytes(thread) - before
            assertEquals(input.candidates.toSet(), plan.candidates.toSet())
            assertTrue(allocated in 1..(symbols.size * 8192L), "Planning allocated $allocated bytes")
        } finally {
            counter.isThreadAllocatedMemoryEnabled = wasEnabled
        }
    }

    @Test
    fun `mandatory test signatures preserve supplied relative relevance`() {
        val main = sourceArtifact("source/Main", "main")
        val first = sourceArtifact("tests/First", "first")
        val second = sourceArtifact("tests/Second", "second")
        val fixture = ContextFixture(listOf(main, first, second), emptyList(), emptyMap())
        val input = ContextInput(
            "fix",
            listOf(
                ContextCandidate(NodeRef.OfArtifact(main.ref), 0.1),
                ContextCandidate(NodeRef.OfArtifact(first.ref), 0.05, ContextRole.TEST),
                ContextCandidate(NodeRef.OfArtifact(second.ref), 0.0125, ContextRole.TEST),
            ),
            tests = listOf(NodeRef.OfArtifact(first.ref), NodeRef.OfArtifact(second.ref)),
        )

        val plan = ContextPlanner(ContextGraph(fixture)).plan(input, ContextLevel.RECOMMENDED)

        assertEquals(0.05, plan.candidates.single { it.node == NodeRef.OfArtifact(first.ref) }.relevance)
        assertEquals(0.0125, plan.candidates.single { it.node == NodeRef.OfArtifact(second.ref) }.relevance)
    }
}
