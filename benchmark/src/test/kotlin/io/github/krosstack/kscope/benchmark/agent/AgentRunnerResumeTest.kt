package io.github.krosstack.kscope.benchmark.agent

import io.github.krosstack.kscope.benchmark.agent.launch.NoopExecutor
import io.github.krosstack.kscope.benchmark.agent.launch.ReferenceExecutor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

internal class AgentRunnerResumeTest : AgentRunnerFixture() {
    @Test
    fun `a completed run is run again when the indexed source or the task package changed`() {
        val task = prepare()
        val store = RunStore(out)

        fun issue() = AgentRunner(settings(), store, single(NoopExecutor(), store)) {}.run(listOf(task), true)
        issue()

        assertEquals(listOf("T01-A-r1"), issue().reused)

        Files.writeString(task.hiddenTests.resolve("src/HiddenTest.txt"), "changed hidden\n")
        assertEquals(listOf("T01-A-r1"), issue().completed)
        assertEquals(listOf("T01-A-r1"), issue().reused)

        Files.writeString(source.resolve("src/Greeting.txt"), "hello again\n")
        git.output(source, "add", "-A")
        val afterSource = issue()
        assertEquals(emptyList(), afterSource.reused)
        assertEquals(listOf("T01-A-r1"), afterSource.completed)
    }

    @Test
    fun `a record from before runs were bound to their inputs is run again`() {
        val task = prepare()
        val store = RunStore(out)
        AgentRunner(settings(), store, single(NoopExecutor(), store)) {}.run(listOf(task), true)
        store.save(assertNotNull(store.load("T01-A-r1")).copy(inputs = ""))

        val again = AgentRunner(settings(), store, single(NoopExecutor(), store)) {}.run(listOf(task), true)

        assertEquals(listOf("T01-A-r1"), again.completed)
    }

    @Test
    fun `a dry run that contradicts its stand-in stays a mismatch when the command is issued again`() {
        val task = prepare()
        val header = "diff --git a/src/Greeting.txt b/src/Greeting.txt\n" +
            "--- a/src/Greeting.txt\n+++ b/src/Greeting.txt\n"
        Files.writeString(task.referencePatch, header + "@@ -1 +1 @@\n-hello\n+hullo\n")
        val store = RunStore(out)

        fun issue() = AgentRunner(settings(), store, single(ReferenceExecutor(), store)) {}.run(listOf(task), true)
        val first = issue()
        val again = issue()

        assertEquals(RunStatus.COMPLETED, store.load("T01-A-r1")?.status)
        assertFalse(first.clean)
        assertEquals(listOf("T01-A-r1"), again.reused)
        assertEquals(first.mismatches, again.mismatches)
        assertFalse(again.clean)
    }
}
