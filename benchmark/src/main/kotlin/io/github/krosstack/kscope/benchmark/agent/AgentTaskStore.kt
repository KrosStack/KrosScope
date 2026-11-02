package io.github.krosstack.kscope.benchmark.agent

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal object AgentTaskStore {
    fun load(root: Path, only: Set<String>? = null): TaskLoad {
        if (!Files.isDirectory(root)) return TaskLoad.Invalid(listOf("The task directory does not exist."))
        val directories = Files.list(root).use { entries ->
            entries.filter { Files.isRegularFile(it.resolve(AgentTask.SPEC)) }.sorted().toList()
        }
        val problems = mutableListOf<String>()
        val tasks = directories.mapNotNull { directory -> read(directory, problems) }
        val selected = if (only == null) tasks else tasks.filter { it.id in only }
        problems += (only.orEmpty() - selected.map { it.id }.toSet()).map { "Unknown task: $it" }
        if (problems.isEmpty() && selected.isEmpty()) problems += "The task directory holds no task package."
        return if (problems.isEmpty()) TaskLoad.Loaded(selected.sortedBy { it.id }) else TaskLoad.Invalid(problems)
    }

    private fun read(directory: Path, problems: MutableList<String>): AgentTask? = try {
        val spec = agentJson.decodeFromString<AgentTaskSpec>(Files.readString(directory.resolve(AgentTask.SPEC)))
        AgentTask(spec, directory).also { task -> problems += consistencyProblems(task, directory) }
    } catch (error: SerializationException) {
        problems += "${directory.fileName}: ${error.message?.lineSequence()?.firstOrNull()}"
        null
    } catch (error: IOException) {
        problems += "${directory.fileName}: ${error.message}"
        null
    }

    private fun consistencyProblems(task: AgentTask, directory: Path): List<String> = buildList {
        val name = directory.fileName.toString()
        if (task.spec.id != name) add("$name: the id differs from the directory name")
        if (!Files.isRegularFile(task.referencePatch)) add("${task.id}: reference.patch is missing")
        if (task.hiddenFiles().isEmpty()) add("${task.id}: there are no hidden tests")
        if (task.spec.failToPass.isEmpty()) add("${task.id}: no fail-to-pass test is listed")
        if (task.spec.goldFiles.isEmpty()) add("${task.id}: no gold file is listed")
    }
}
