package io.github.krosstack.kscope.cli.lookup

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files

internal const val APPLICATION_FOLDER = "application/lib/src/main/kotlin"

internal fun <T> inTaskRepositories(block: (NestedWorkspace) -> T): T = inRepositories(
    listOf("TaskStore", "TaskReminders"),
    listOf("TaskSync", "TaskClock"),
    block,
)

internal fun <T> inQualifiedTaskRepositories(block: (NestedWorkspace) -> T): T = inRepositories(
    listOf("tasks.TaskStore", "tasks.TaskReminders"),
    listOf("sync.TaskSync", "sync.TaskClock"),
    block,
)

private fun <T> inRepositories(application: List<String>, library: List<String>, block: (NestedWorkspace) -> T): T =
    inTemporaryFolder { base ->
        assumeTrue(gitAnswers(), "git is not available")
        val workspace = NestedWorkspace(base)
        repository(workspace.root.resolve("application"), application)
        repository(workspace.root.resolve("library"), library)
        Files.createDirectories(workspace.root.resolve("notes"))
        scanned(workspace.root)
        block(workspace)
    }

internal fun NestedWorkspace.narrowedLine(directory: String): String =
    "Using the index of a parent folder: $index. Paths are relative to ${slashed(root)}. " +
        "Answer limited to $directory/; pass --workspace ${slashed(root)} for the whole workspace.\n"

internal fun NestedWorkspace.widenedLine(directory: String, query: String): String =
    "Using the index of a parent folder: $index. The answer covers the whole workspace ${slashed(root)}, and paths " +
        "are relative to it. Nothing in $directory/ matches \"$query\", so the search was widened to the whole " +
        "workspace.\n"
