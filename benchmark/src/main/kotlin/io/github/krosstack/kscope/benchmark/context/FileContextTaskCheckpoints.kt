package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.context.TokenCount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
private data class CheckpointBudget(val budget: TokenCount, val result: TaskContextResult)

@Serializable
private data class CheckpointRecord(
    val request: ContextCheckpointRequest,
    val identity: String,
    val budgets: List<CheckpointBudget>,
    val schemaVersion: Int = 1,
)

public class FileContextTaskCheckpoints(
    private val directory: Path,
    private val identity: String,
    private val resume: Boolean = true,
) : ContextTaskCheckpoints {
    override fun load(request: ContextCheckpointRequest): Map<TokenCount, TaskContextResult>? {
        val path = path(request)
        if (!resume || !Files.isRegularFile(path)) return null
        val record = json.decodeFromString<CheckpointRecord>(Files.readString(path))
        return if (record.request == request && record.identity == identity && valid(record)) {
            record.budgets.associate { it.budget to it.result }
        } else {
            null
        }
    }

    override fun save(request: ContextCheckpointRequest, results: Map<TokenCount, TaskContextResult>) {
        Files.createDirectories(directory)
        val record = CheckpointRecord(
            request,
            identity,
            results.entries.sortedBy { it.key.value }.map {
                CheckpointBudget(it.key, it.value)
            },
        )
        val target = path(request)
        val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(temporary, json.encodeToString(record) + "\n")
        publish(temporary, target)
    }

    private fun valid(record: CheckpointRecord): Boolean = record.schemaVersion == 1 && record.budgets.isNotEmpty() &&
        record.budgets.map { it.budget }.distinct().size == record.budgets.size &&
        record.budgets.all { it.result.id == record.request.taskId }

    private fun path(request: ContextCheckpointRequest): Path {
        val key = "$identity:${request.set}:${request.taskId}:${request.fingerprint}"
        return directory.resolve(contextFingerprint(key) + ".json")
    }

    private fun publish(temporary: Path, target: Path) {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}
