package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealIdentifier
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

internal class SemanticContextOptions(val flags: Map<String, String>) {
    val output: Path = Path.of(flags.getValue("--out"))
    val scratch: Path = Path.of(flags.getValue("--scratch"))
    val split: RealSplit = RealSplit.valueOf(flags.getValue("--split").uppercase())
    val repositories: Set<String> = flags["--repositories"]?.split(',')?.toSet().orEmpty()

    fun tasks(manifest: RealRepositoryManifest): List<RealCommitTask> = manifest.tasks.filter {
        it.split == split && (repositories.isEmpty() || it.repository in repositories)
    }.sortedWith(compareBy({ it.repository }, { it.committedAt }, { it.commit }, { it.id }))

    fun queries(tasks: List<RealCommitTask>): Map<String, String?> =
        tasks.groupBy { it.repository }.toSortedMap().flatMap { (repository, selected) ->
            val path = Path.of(flags.getValue("--paraphrases")).resolve(split.name).resolve("$repository.jsonl")
            val rows = Files.readAllLines(path).filter(String::isNotBlank).map(::row)
            require(rows.map { it.first }.distinct().size == rows.size) { "Duplicate paraphrase IDs." }
            val queries = rows.toMap()
            selected.map { task ->
                require(queries.containsKey(task.id)) { "Missing paraphrase row: ${task.id}" }
                task.id to queries[task.id]?.also { require(it.isNotBlank()) { "Blank paraphrase: ${task.id}" } }
            }
        }.toMap().toSortedMap()

    private fun row(line: String): Pair<String, String?> {
        val row = KscopeJson.parseToJsonElement(line) as? JsonObject
        val id = (row?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val paraphrase = row?.get("paraphrase") as? JsonPrimitive
        require(id != null && paraphrase != null && (paraphrase is JsonNull || paraphrase.isString)) {
            "Malformed paraphrase row: $line"
        }
        return id to paraphrase.takeUnless { it is JsonNull }?.content
    }

    companion object {
        val METHODS = listOf(ContextBenchmarkMethod.FULL_COMPILER, ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER)

        fun parse(args: List<String>): SemanticContextOptions {
            require(args.firstOrNull() == "real-repos-context-semantic") { "Expected real-repos-context-semantic." }
            val pairs = args.drop(1).chunked(2)
            require(pairs.all { it.size == 2 && it.last().isNotBlank() && !it.last().startsWith("--") })
            val flags = pairs.associate { it.first() to it.last() }
            val required = setOf("--manifest", "--out", "--scratch", "--split", "--checkpoint-key", "--paraphrases")
            require(flags.size == pairs.size && flags.keys.containsAll(required)) { "Missing or duplicate flags." }
            require((required + "--repositories").containsAll(flags.keys)) { "Unknown semantic context flag." }
            require(flags["--split"] in setOf("dev", "test")) { "Split must be dev or test." }
            require(Regex("[0-9a-f]{64}").matches(flags.getValue("--checkpoint-key"))) { "Invalid checkpoint key." }
            require(flags["--repositories"]?.split(',')?.all(RealIdentifier::isValid) != false)
            return SemanticContextOptions(flags)
        }
    }
}
