package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.RepoPath
import java.nio.file.Path

internal object FusionReplayExport {
    private const val TOP_K = 10
    private val COMMITS = Regex("(?:[0-9a-f]{40}|[0-9a-f]{64})")
    private val REPOSITORY_ID = Regex("([A-Za-z0-9-]+)\\.([A-Za-z0-9._-]+)")

    fun write(expected: SemanticRecord, queries: Map<String, SemanticRecord.Query>, output: Path) {
        require(queries.keys == setOf("original", "paraphrase"))
        val cases = queries.mapNotNull { (kind, query) ->
            require(expected.queries.containsKey(kind) && expected.queries[kind]?.text == query.text)
            if (query.text == null) {
                null
            } else {
                case(expected, kind, query.rankings.getValue("structural"), query.rankings.getValue("semantic"))
            }
        }
        cases.forEach { write(it, output) }
    }

    fun write(expected: SemanticRecord, kind: String, structural: List<String>, semantic: List<String>, output: Path) {
        write(case(expected, kind, structural, semantic), output)
    }

    private fun case(
        expected: SemanticRecord,
        kind: String,
        structural: List<String>,
        semantic: List<String>,
    ): FusionReplayCase {
        val task = expected.task
        require(task.split == RealSplit.DEV && task.workspace.isEmpty() && expected.failure == null)
        val (owner, name) = requireNotNull(REPOSITORY_ID.matchEntire(task.repository)).destructured
        require(COMMITS.matches(task.parent) && COMMITS.matches(task.commit))
        require(kind in setOf("original", "paraphrase"))
        val stored = requireNotNull(expected.queries[kind])
        requireNotNull(stored.text)
        val fused = SemanticRanking.fuse(structural, semantic).take(TOP_K)
        require(stored.rankings["structural"] == structural.take(TOP_K))
        require(stored.rankings["semantic"] == semantic.take(TOP_K))
        require(stored.rankings["structural-semantic"] == fused)
        (structural + semantic + fused).forEach { require(it.isNotBlank() && RepoPath(it).value == it) }
        return FusionReplayCase(
            "dev-replay-${SemanticCheckpoint.digest(task.id)}-$kind",
            "$owner/$name",
            task.parent,
            task.commit,
            kind,
            structural,
            semantic,
            stored.rankings.getValue("structural-semantic"),
        )
    }

    private fun write(replay: FusionReplayCase, output: Path) {
        SemanticCheckpoint.write(output.resolve("${replay.id}.jsonl"), KscopeJson.encodeToString(replay) + "\n")
    }
}
