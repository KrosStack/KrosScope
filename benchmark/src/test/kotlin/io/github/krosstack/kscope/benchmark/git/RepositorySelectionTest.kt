package io.github.krosstack.kscope.benchmark.git

import kotlin.test.Test
import kotlin.test.assertEquals

class RepositorySelectionTest {
    @Test
    fun `every variable that selects a repository is removed whatever its case`() {
        val selecting = listOf(
            "GIT_DIR",
            "GIT_WORK_TREE",
            "GIT_INDEX_FILE",
            "GIT_OBJECT_DIRECTORY",
            "GIT_ALTERNATE_OBJECT_DIRECTORIES",
            "GIT_COMMON_DIR",
            "GIT_NAMESPACE",
            "GIT_CEILING_DIRECTORIES",
        )
        val kept = mapOf("PATH" to "p", "GIT_AUTHOR_NAME" to "a")
        val environment = selecting.associateWith { "x" } + mapOf("git_dir" to "y") + kept

        assertEquals(kept, RepositorySelection.cleared(environment))
    }

    @Test
    fun `a process builder keeps the given variables except the ones that select a repository`() {
        val builder = ProcessBuilder("git")

        RepositorySelection.applyTo(builder, mapOf("GIT_DIR" to "x", "KEEP" to "k"))

        assertEquals(mapOf("KEEP" to "k"), builder.environment().toMap())
    }
}
