package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.discovery.ProcessRunner
import io.github.krosstack.kscope.engine.structure.TestGit
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class WorkspaceStampPollingTest {
    @Test
    fun `a reusable real stamp runs one git process per poll after the first`() {
        TestWorkspace().use { workspace ->
            TestGit.output(workspace.root, "init", "-q")
            workspace.write("Source.kt", "class Source")
            val commands = mutableListOf<List<String>>()
            val runner = ProcessRunner(onStart = { commands += it })
            workspace.engine().use { engine ->
                val source = engine.workspaceState(runner)
                val initial = source.stamp()
                val first = commands.size
                repeat(3) { poll ->
                    workspace.write("Source.kt", "class Source$poll")
                    assertNotEquals(initial, source.stamp())
                    assertEquals(first + poll + 1, commands.size)
                }
                assertEquals(1, commands.count { "rev-parse" in it })
                assertEquals(4, commands.count { "ls-files" in it })
                assertFalse(Files.exists(workspace.indexDirectory))
            }
        }
    }
}
