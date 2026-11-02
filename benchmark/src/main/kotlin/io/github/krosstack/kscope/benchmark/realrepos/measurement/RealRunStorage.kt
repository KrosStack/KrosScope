package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.model.context.TokenCount
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat

@Serializable
private data class RealRunIdentity(
    val identity: String,
    val manifest: String,
    val schemaVersion: Int = 1,
    val budgets: List<Int> = ContextBenchmarkBudgets.values.map { it.value },
)

internal open class RealRunStorage(output: Path) {
    private val paths = RealRunPaths(output)
    private val root = paths.root
    private val json = Json { encodeDefaults = true }

    fun bind(
        manifest: RealRepositoryManifest,
        identity: String,
        budgets: List<TokenCount> = ContextBenchmarkBudgets.values,
    ) {
        val key = realRunKey(json.encodeToString(manifest))
        val expected = RealRunIdentity(identity, key, budgets = budgets.map { it.value })
        val marker = paths.inside(".identity")
        if (Files.exists(marker)) {
            require(json.decodeFromString<RealRunIdentity>(Files.readString(marker)) == expected) {
                "Run output belongs to a different freeze identity, manifest or budget set."
            }
        } else {
            Files.createDirectories(root)
            publish(marker, json.encodeToString(expected))
        }
    }

    fun load(task: RealCommitTask, identity: String): RealRepositoryMeasurement? {
        val path = recordPath(task)
        if (!Files.isRegularFile(path)) return null
        val record = json.decodeFromString<RealRepositoryMeasurement>(Files.readString(path))
        require(record.schemaVersion == 1 && record.task == task && record.identity == identity) {
            "Task record differs from the frozen task."
        }
        return record
    }

    fun save(record: RealRepositoryMeasurement) {
        publish(recordPath(record.task), json.encodeToString(record))
    }

    fun records(tasks: List<RealCommitTask>): List<RealRepositoryMeasurement> = RealRecordList(tasks.map(::recordPath))

    fun scratch(repository: String): Path = paths.inside("scratch/${realRunKey(repository)}")

    fun index(repository: String): Path = paths.inside("indexes/${realRunKey(repository)}")

    open fun checkpoints(task: String): Path = paths.inside("context-checkpoints/${realRunKey(task)}")

    fun gitOutput(): Path = Files.createTempFile(paths.ensure("pending"), "git-", ".log")

    fun disabledHooks(): Path = paths.ensure("disabled-hooks")

    private fun recordPath(task: RealCommitTask): Path = paths.inside("tasks/${realRunKey(task.id)}.json")

    private fun publish(target: Path, contents: String) {
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(paths.ensure("pending"), "record-", ".pending")
        try {
            Files.writeString(temporary, contents + "\n", Charsets.UTF_8)
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

private class RealRunPaths(output: Path) {
    val root: Path = canonicalFuturePath(output)

    fun ensure(relative: String): Path = inside(relative).also { Files.createDirectories(it) }

    fun inside(relative: String): Path {
        val path = canonicalFuturePath(root.resolve(relative))
        require(path.startsWith(root)) { "Run path escapes the output directory." }
        return path
    }
}

internal object RealRepositoryRecords {
    fun read(directory: Path): List<RealRepositoryMeasurement> {
        val tasks = directory.resolve("tasks")
        if (!Files.isDirectory(tasks)) return emptyList()
        val paths = Files.list(tasks).use { files -> files.filter { Files.isRegularFile(it) }.sorted().toList() }
        return RealRecordList(paths)
    }
}

private class RealRecordList(private val paths: List<Path>) : AbstractList<RealRepositoryMeasurement>() {
    override val size: Int get() = paths.size

    override fun get(index: Int): RealRepositoryMeasurement =
        Json.decodeFromString<RealRepositoryMeasurement>(Files.readString(paths[index], Charsets.UTF_8))
}

private fun realRunKey(text: String): String = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)),
)
