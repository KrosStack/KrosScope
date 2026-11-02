package io.github.krosstack.kscope.benchmark.agent

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class RunStore(private val root: Path) {
    private val runs = root.resolve("runs")

    fun bind(binding: RunBinding): String? {
        Files.createDirectories(runs)
        if (!Files.exists(root.resolve(BINDING))) {
            write(root.resolve(BINDING), agentJson.encodeToString(binding))
            return null
        }
        return if (binding() == binding) null else MISMATCH
    }

    fun binding(): RunBinding? {
        val marker = root.resolve(BINDING)
        if (!Files.isRegularFile(marker)) return null
        return try {
            agentJson.decodeFromString<RunBinding>(Files.readString(marker))
        } catch (_: SerializationException) {
            null
        }
    }

    fun load(key: String): RunRecord? {
        val file = runs.resolve("$key.json")
        if (!Files.isRegularFile(file)) return null
        return try {
            agentJson.decodeFromString<RunRecord>(Files.readString(file))
        } catch (_: SerializationException) {
            null
        }
    }

    fun save(record: RunRecord) {
        write(runs.resolve("${record.key}.json"), agentJson.encodeToString(record))
    }

    fun logDirectory(key: String): Path = root.resolve("logs").resolve(key)

    fun records(): List<RunRecord> {
        if (!Files.isDirectory(runs)) return emptyList()
        return Files.list(runs).use { files ->
            files.filter { it.fileName.toString().endsWith(".json") }.sorted().toList()
        }.mapNotNull { file ->
            try {
                agentJson.decodeFromString<RunRecord>(Files.readString(file))
            } catch (_: SerializationException) {
                null
            } catch (_: IOException) {
                null
            }
        }
    }

    private companion object {
        const val BINDING = "binding.json"
        const val MISMATCH = "The run directory belongs to another agent, model, budget or prompt, or is unreadable."
    }

    private fun write(target: Path, text: String) {
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        Files.writeString(temporary, text + "\n")
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

internal class PlannedRun(val task: AgentTask, val arm: Arm, val repeat: Int) {
    val key: String get() = RunRecord.runKey(task.id, arm, repeat)
}

internal object RunPlan {
    fun build(tasks: List<AgentTask>, arms: List<Arm>, repeats: Int, seed: Long): List<PlannedRun> =
        (1..repeats).flatMap { repeat ->
            tasks.flatMap { task ->
                val random = java.util.Random(seed * SEED_MIX + task.id.hashCode() * REPEAT_MIX + repeat)
                arms.shuffled(random).map { arm -> PlannedRun(task, arm, repeat) }
            }
        }

    private const val SEED_MIX = 1_000_003L
    private const val REPEAT_MIX = 31L
}
