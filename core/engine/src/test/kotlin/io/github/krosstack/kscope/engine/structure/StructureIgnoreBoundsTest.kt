package io.github.krosstack.kscope.engine.structure

import io.github.krosstack.kscope.engine.discovery.ProcessResult
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructureIgnoreBoundsTest {
    private class AskedPaths : GitIgnoreRunner {
        val asked = mutableListOf<String>()

        override fun run(command: List<String>, workingDirectory: Path, input: ByteArray): ProcessResult {
            if ("rev-parse" in command) return ProcessResult.Completed(0, "true\n".toByteArray(Charsets.UTF_8))
            asked += String(input, Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }
            return ProcessResult.Completed(1, byteArrayOf())
        }
    }

    private fun workspaceOfTen(): StructureWorkspace = StructureWorkspace().also { workspace ->
        workspace.repository(".")
        for (index in 1..DIRECTORIES) workspace.file("d$index/package.json")
    }

    @Test
    fun `Git is asked about no more directories than the walk may enter`() = workspaceOfTen().use { workspace ->
        val runner = AskedPaths()

        val found = StructureScanner(4, LIMIT, "git", runner).scan(workspace.root)

        val directoriesAsked = runner.asked.filter { it.startsWith("d") && "/" !in it }
        assertTrue(directoriesAsked.size < LIMIT, runner.asked.toString())
        assertTrue(found.truncated)
        assertTrue(found.directoriesVisited <= LIMIT)
    }

    @Test
    fun `nothing is reported from a directory that Git was not asked about`() = workspaceOfTen().use { workspace ->
        val runner = AskedPaths()

        val found = StructureScanner(4, LIMIT, "git", runner).scan(workspace.root)

        val reported = found.projects.map { it.directory }
        assertTrue(reported.isNotEmpty())
        assertTrue(runner.asked.containsAll(reported), "reported $reported, asked ${runner.asked}")
        assertTrue(runner.asked.containsAll(reported.map { "$it/package.json" }))
    }

    @Test
    fun `a limit that the directory does not reach leaves the report complete`() = workspaceOfTen().use { workspace ->
        val runner = AskedPaths()

        val found = StructureScanner(4, DIRECTORIES + 1, "git", runner).scan(workspace.root)

        assertFalse(found.truncated)
        assertEquals(DIRECTORIES, found.projects.size)
        assertEquals(DIRECTORIES, runner.asked.count { it.startsWith("d") && "/" !in it })
    }

    private companion object {
        const val DIRECTORIES = 10
        const val LIMIT = 3
    }
}
