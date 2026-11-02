package io.github.krosstack.kscope.benchmark.workspace.tasks

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/** Reads the task files of the workspace of repositories, which sit in `tasks/`, ordered by id. */
internal object WorkspaceTaskLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(goldDirectory: Path): List<WorkspaceTaskFile> = Files.list(goldDirectory.resolve(TASKS)).use { files ->
        files.filter { it.fileName.toString().endsWith(JSON_SUFFIX) }.map(::decode).toList()
    }.sortedBy { it.id }

    private fun decode(file: Path): WorkspaceTaskFile =
        json.decodeFromString(WorkspaceTaskFile.serializer(), Files.readString(file))

    private const val TASKS = "tasks"
    private const val JSON_SUFFIX = ".json"
}
