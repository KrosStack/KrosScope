package io.github.krosstack.kscope.benchmark.agent

import java.nio.file.Files
import java.nio.file.Path

internal class AgentTask(val spec: AgentTaskSpec, val directory: Path) {
    val id: String get() = spec.id
    val hiddenTests: Path get() = directory.resolve(HIDDEN)
    val referencePatch: Path get() = directory.resolve(PATCH)

    fun hiddenFiles(): List<String> {
        if (!Files.isDirectory(hiddenTests)) return emptyList()
        return Files.walk(hiddenTests).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .map { hiddenTests.relativize(it).toString().replace('\\', '/') }
                .toList()
        }.sorted()
    }

    fun patchFiles(): List<String> = Files.readAllLines(referencePatch)
        .filter { it.startsWith("diff --git ") }
        .map { it.substringAfter(" b/") }
        .sorted()

    fun contentDigest(): String = Fingerprint.of(
        packageFiles().asSequence().flatMap { (name, file) ->
            sequenceOf(name.toByteArray(Charsets.UTF_8), Files.readAllBytes(file))
        },
    )

    private fun packageFiles(): List<Pair<String, Path>> = Files.walk(directory).use { paths ->
        paths.filter { Files.isRegularFile(it) }
            .map { directory.relativize(it).toString().replace('\\', '/') to it }
            .toList()
    }.sortedBy { it.first }

    companion object {
        const val HIDDEN = "hidden"
        const val PATCH = "reference.patch"
        const val SPEC = "task.json"
    }
}

internal sealed interface TaskLoad {
    data class Loaded(val tasks: List<AgentTask>) : TaskLoad

    data class Invalid(val problems: List<String>) : TaskLoad
}
