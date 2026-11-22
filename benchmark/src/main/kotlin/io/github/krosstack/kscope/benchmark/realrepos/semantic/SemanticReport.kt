package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.benchmark.stats.ComparisonMetric
import io.github.krosstack.kscope.benchmark.stats.PairedBootstrap
import io.github.krosstack.kscope.benchmark.stats.PairedValue
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.nio.file.Path

internal object SemanticReport {
    private val metricNames = listOf("hit10", "recall10", "ndcg10")
    private val comparisons = mapOf("hybrid" to "lexical", "structural-semantic" to "structural")

    fun write(records: List<SemanticRecord>, output: Path): String {
        val groups = records.groupBy { it.task.repository }.toSortedMap() + ("pooled" to records)
        val report = buildJsonObject {
            put("metricOrder", KscopeJson.encodeToJsonElement(metricNames))
            put("pipeline", records.first().pipeline)
            put("binding", records.first().binding)
            put("bootstrap", "task-paired, pooled unstratified, seed 20261005, 10000 resamples, linear 95% percentiles")
            put("groups", buildJsonObject { groups.forEach { (name, rows) -> put(name, group(rows)) } })
        }.toString()
        SemanticCheckpoint.write(output.resolve("semantic-report.json"), report)
        return report
    }

    private fun group(records: List<SemanticRecord>) = buildJsonObject {
        val complete = records.filter { it.failure == null }
        put("expected", records.size)
        put("complete", complete.size)
        put("embedded", complete.sumOf { it.embedded })
        put(
            "seconds",
            KscopeJson.encodeToJsonElement(
                listOf("update", "embedding", "scoring").associateWith { phase ->
                    complete.map { it.seconds.getValue(phase) }.average().takeIf { it.isFinite() }
                },
            ),
        )
        for (kind in listOf("original", "paraphrase")) put(kind, queries(complete, kind))
    }

    private fun queries(complete: List<SemanticRecord>, kind: String) = buildJsonObject {
        val rows = complete.filter { it.queries.getValue(kind).text != null }
        put("measured", rows.size)
        put("unresolved", complete.size - rows.size)
        val scores = rows.flatMap { it.queries.getValue(kind).metrics.entries }
            .groupBy({ it.key }, { it.value })
        put(
            "means",
            KscopeJson.encodeToJsonElement(
                scores.mapValues { (_, values) ->
                    metricNames.indices.map { index -> values.map { it[index] }.average() }
                },
            ),
        )
        comparisons.forEach { (candidate, baseline) ->
            put(
                "$candidate-minus-$baseline",
                buildJsonObject {
                    metricNames.forEachIndexed { index, metric ->
                        put(
                            metric,
                            comparison(rows, kind, index, candidate to baseline),
                        )
                    }
                },
            )
        }
    }

    private fun comparison(records: List<SemanticRecord>, kind: String, index: Int, arms: Pair<String, String>) =
        buildJsonObject {
            val pairs = records.map { row ->
                val metrics = row.queries.getValue(kind).metrics
                PairedValue(row.task.id, metrics.getValue(arms.first)[index], metrics.getValue(arms.second)[index])
            }
            val result = PairedBootstrap.compare(ComparisonMetric.RECALL_10, pairs, arms.first, arms.second)
            put("delta", KscopeJson.encodeToJsonElement(result.pointDelta))
            put("lower95", KscopeJson.encodeToJsonElement(result.lower95))
            put("upper95", KscopeJson.encodeToJsonElement(result.upper95))
            put("wins", pairs.count { it.candidate > it.baseline })
            put("ties", pairs.count { it.candidate == it.baseline })
            put("losses", pairs.count { it.candidate < it.baseline })
        }
}
