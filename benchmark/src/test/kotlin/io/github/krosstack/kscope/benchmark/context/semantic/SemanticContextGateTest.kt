package io.github.krosstack.kscope.benchmark.context.semantic

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.context.ContextBudgetResult
import io.github.krosstack.kscope.benchmark.context.ContextGoldSpec
import io.github.krosstack.kscope.benchmark.context.ContextMeasurementDefinition
import io.github.krosstack.kscope.benchmark.context.ContextMethodResult
import io.github.krosstack.kscope.benchmark.context.ContextReport
import io.github.krosstack.kscope.benchmark.context.ContextScores
import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.context.ContextSourceIssue
import io.github.krosstack.kscope.benchmark.context.ContextSourceIssueReason
import io.github.krosstack.kscope.benchmark.context.FullFileCoverageCosts
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.contextBenchmarkExitCode
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.context.SemanticContextRecord
import io.github.krosstack.kscope.benchmark.realrepos.context.SemanticContextReport
import io.github.krosstack.kscope.benchmark.stats.ComparisonMetric
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SemanticContextGateTest {
    @TempDir
    lateinit var output: Path

    @Test
    @DisplayName("a loss of exactly 0.01 passes the regression boundary")
    fun `a loss of exactly 0_01 passes the regression boundary`() {
        val gate = SemanticContextGate.evaluate(listOf(record("one", 0.69)))
        assertTrue(gate.regressionPassed)
        assertTrue(SemanticContextGate.Failure.REGRESSION !in gate.failures)
        assertTrue(gate.budgets.filter { it.pooled }.all { it.delta == "-0.01" })
        assertEquals(0, gate.cellsBelowLimit)
        assertEquals(0, gate.repositoriesBelowLimit)
        assertEquals(32, gate.budgets.size)
        assertTrue(gate.budgets.all { it.delta == "-0.01" })
        assertTrue(gate.budgets.all { it.comparison.candidateMean == 0.69 && it.comparison.baselineMean == 0.7 })
    }

    @Test
    fun `a repository loss is evidence when the pooled mean passes`() {
        val gate = SemanticContextGate.evaluate(listOf(record("one", 0.689), record("two", 0.8)))
        assertTrue(gate.regressionPassed)
        assertTrue(gate.passed)
        assertTrue(SemanticContextGate.Failure.REGRESSION !in gate.failures)
        assertEquals(16, gate.cellsBelowLimit)
        assertEquals(1, gate.repositoriesBelowLimit)
        assertTrue(
            gate.budgets.filter { it.repository == "pooled" }.all { assertNotNull(it.comparison.pointDelta) > 0 },
        )
        assertTrue(
            SemanticContextGate.markdown(gate).contains(
                "Failures: none.\nPer-repository cells below the limit: 16 in 1 repositories " +
                    "(evidence; the pooled comparison decides).",
            ),
        )
        val namedPooled = SemanticContextGate.evaluate(
            listOf(record("pooled", 0.689), record("one", 0.689), record("two", 0.8)),
        )
        assertTrue(namedPooled.regressionPassed)
        assertEquals(32, namedPooled.cellsBelowLimit)
        assertEquals(2, namedPooled.repositoriesBelowLimit)
    }

    @Test
    fun `a pooled loss below the limit fails the regression boundary`() {
        val gate = SemanticContextGate.evaluate(listOf(record("one", 0.689), record("two", 0.69)))
        assertTrue(
            gate.budgets.filter { it.pooled }.all { assertNotNull(it.delta).toBigDecimal() < "-0.01".toBigDecimal() },
        )
        assertFalse(gate.regressionPassed)
        assertFalse(gate.passed)
        assertTrue(SemanticContextGate.Failure.REGRESSION in gate.failures)
        assertEquals(16, gate.cellsBelowLimit)
        assertEquals(1, gate.repositoriesBelowLimit)
    }

    @Test
    fun `recall and precision are checked at every budget and repository`() {
        for (budget in ContextBenchmarkBudgets.values.indices) {
            for (precision in listOf(false, true)) {
                val bad = change(record("one", 0.8)) { set ->
                    set.copy(
                        budgets = set.budgets.mapIndexed { index, row ->
                            if (index != budget) {
                                row
                            } else {
                                row.copy(
                                    tasks = row.tasks.map { task ->
                                        val arm = task.methods.getValue(ON)
                                        val score = if (precision) {
                                            arm.scores.copy(tokenPrecision = 0.68)
                                        } else {
                                            arm.scores.copy(goldContextRecall = 0.68)
                                        }
                                        task.copy(methods = task.methods + (ON to arm.copy(scores = score)))
                                    },
                                )
                            }
                        },
                    )
                }
                val gate = SemanticContextGate.evaluate(listOf(bad, record("two", 0.9)))
                assertTrue(gate.regressionPassed)
                assertTrue(
                    gate.budgets.any {
                        it.tokens == ContextBenchmarkBudgets.values[budget].value && it.repository == "one" &&
                            it.delta == "-0.02" &&
                            it.comparison.metric == if (precision) {
                                ComparisonMetric.TOKEN_PRECISION
                            } else {
                                ComparisonMetric.GOLD_CONTEXT_RECALL
                            }
                    },
                )
            }
        }
    }

    @Test
    fun `missing tasks arms budgets or source proof cannot pass`() {
        val good = record("one", 0.8)
        val missing = listOf(
            good.copy(original = null),
            change(good) { it.copy(budgets = it.budgets.dropLast(1)) },
            changeTasks(good) { it.copy(methods = it.methods - ON) },
            changeTasks(good) { task ->
                val arm = task.methods.getValue(ON)
                task.copy(
                    methods = task.methods + (
                        ON to arm.copy(
                            scores = arm.scores.copy(
                                sourceIssues = listOf(ContextSourceIssue(null, ContextSourceIssueReason.TEXT_MISMATCH)),
                            ),
                        )
                        ),
                )
            },
            good.copy(inventory = null),
            good.copy(pipeline = null),
            good.copy(pipeline = pipeline.copy(generation = null)),
        )
        assertFalse(SemanticContextGate.evaluate(emptyList()).passed)
        assertFalse(
            SemanticContextGate.evaluate(listOf(good), listOf(good.task, good.task.copy(id = "missing"))).passed,
        )
        for (record in missing) assertFalse(SemanticContextGate.evaluate(listOf(record)).passed)
        val absentSource = SemanticContextGate.evaluate(listOf(good.copy(inventory = null)))
        assertTrue(SemanticContextGate.Failure.SOURCE in absentSource.failures)
        val unmeasured = SemanticContextGate.evaluate(listOf(record("one", Double.NaN), record("two", 0.8)))
        assertTrue(unmeasured.budgets.any { !it.pooled && it.delta == null })
        assertTrue(unmeasured.budgets.any { it.pooled && it.delta == null })
        assertFalse(unmeasured.regressionPassed)
        assertTrue(SemanticContextGate.Failure.REGRESSION in unmeasured.failures)
        assertEquals(0, unmeasured.cellsBelowLimit)
        assertEquals(0, unmeasured.repositoriesBelowLimit)
    }

    @Test
    fun `ON requires actual complete matching provider and model evidence`() {
        val statuses = listOf(
            null,
            SemanticStatus.NOT_INSTALLED,
            status.copy(state = SemanticState.BEHIND),
            status.copy(behindChunks = 1),
            status.copy(provider = "other"),
            status.copy(model = pipeline.model.copy(revision = "other")),
        )
        for (semantic in statuses) {
            val bad = changeTasks(record("one", 0.8)) { task ->
                task.copy(methods = task.methods + (ON to task.methods.getValue(ON).copy(semantic = semantic)))
            }
            assertTrue(SemanticContextGate.Failure.SEMANTIC in SemanticContextGate.evaluate(listOf(bad)).failures)
        }
    }

    @Test
    fun `ON Tier 1 is checked separately against both BM25 baselines`() {
        val result = reference()
        val gate = SemanticContextGate.evaluate(result)
        assertTrue(gate.regressionPassed && gate.onTier1Passed == true)
        assertFalse(result.tier1Passed)
        for (baseline in listOf(ContextBenchmarkMethod.BM25_RAW, ContextBenchmarkMethod.BM25_ENGLISH)) {
            val tied = result.copy(
                sets = result.sets.map { set ->
                    set.copy(
                        budgets = set.budgets.map { row ->
                            row.copy(
                                tasks = row.tasks.map { task ->
                                    task.copy(methods = task.methods + (baseline to arm(0.8)))
                                },
                            )
                        },
                    )
                },
            )
            val failed = SemanticContextGate.evaluate(tied)
            assertTrue(failed.regressionPassed)
            assertEquals(false, failed.onTier1Passed)
        }
        assertFalse(SemanticContextGate.evaluate(result.copy(sets = result.sets.take(1))).passed)
        assertFalse(SemanticContextGate.evaluate(result, setOf("task", "missing")).passed)
        val missing = SemanticContextGate.evaluate(reference(listOf(ContextBenchmarkMethod.BM25_ENGLISH)))
        assertTrue(missing.regressionPassed)
        assertEquals(false, missing.onTier1Passed)
    }

    @Test
    fun `complete goldless records remain paired and counted with zero recall`() {
        val goldless = changeTasks(record("one", 0.8)) { task ->
            task.copy(
                resolvedGoldFiles = emptyList(),
                excludedMissingFiles = listOf("gold"),
                methods = task.methods.mapValues { (_, arm) ->
                    arm.copy(
                        scores = arm.scores.copy(goldContextRecall = 0.0),
                    )
                },
            )
        }
        assertTrue(goldless.complete)
        val gate = SemanticContextGate.evaluate(
            listOf(
                goldless,
                record(
                    "one",
                    0.8,
                ).copy(
                    task = realTask("one", "other"),
                    original = set(BenchmarkSet.ORIGINAL, "other"),
                    paraphrase = set(BenchmarkSet.HELD_OUT, "other"),
                ),
            ),
        )
        assertTrue(gate.passed)
        assertEquals(32, gate.budgets.size)
        assertTrue(gate.budgets.all { it.comparison.pairs.size == 2 })
    }

    @Test
    fun `semantic command exits from ON gates while OFF keeps its original verdict`() {
        assertEquals(0, contextBenchmarkExitCode(reference(), MlMode.ON))
        assertEquals(1, contextBenchmarkExitCode(reference(), MlMode.OFF))
        val invalid = reference().copy(pipeline = null, tier1Passed = true)
        assertEquals(1, contextBenchmarkExitCode(invalid, MlMode.ON))
        assertEquals(0, contextBenchmarkExitCode(invalid, MlMode.OFF))
    }

    @Test
    fun `reports keep the OFF verdict and expose separate ON verdicts without statements`() {
        val markdown = ContextReport.markdown(reference())
        assertTrue(markdown.contains("Tier 1: FAIL"))
        assertTrue(markdown.contains("ON Tier 1: PASS"))
        assertTrue(markdown.contains("ON/OFF regression: PASS"))
        val incomplete = ContextReport.markdown(reference(), setOf("task", "missing"))
        assertTrue(incomplete.contains("ON/OFF regression: FAIL"))
        assertTrue(SemanticContextReport.write(listOf(record("one", 0.689), record("two", 0.8)), output))
        val json = Files.readString(output.resolve("semantic-context-report.json"))
        assertFalse(json.contains("private query text"))
        assertTrue(json.contains("regressionPassed"))
        val verdict = KscopeJson.parseToJsonElement(json).jsonObject.getValue("verdict").jsonObject
        assertEquals("16", verdict.getValue("cellsBelowLimit").jsonPrimitive.content)
        assertEquals("1", verdict.getValue("repositoriesBelowLimit").jsonPrimitive.content)
        assertTrue(Files.readString(output.resolve("semantic-context-report.md")).contains("ON Tier 1: NOT RUN"))
    }

    private companion object {
        val HASH = ContentHash("a".repeat(64))
        val TOKENIZER = TokenizerId("test")
        val OFF = ContextBenchmarkMethod.FULL_COMPILER
        val ON = ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER
        val pipeline = ContextPipelineIdentity(
            "provider",
            ModelIdentity("model", "revision", 2, "int8", 1),
            HASH,
            mapOf("worker" to HASH),
            mapOf("model" to HASH),
            mapOf("jvm" to "test"),
            mapOf("ranking" to "frozen"),
            HASH,
        )
        val status = SemanticStatus(SemanticState.USED, pipeline.provider, pipeline.model)
        private fun record(repository: String, on: Double) = SemanticContextRecord(
            realTask(repository),
            HASH,
            pipeline,
            HASH,
            set(BenchmarkSet.ORIGINAL, on = on),
            set(BenchmarkSet.HELD_OUT, on = on),
        )

        private fun realTask(repository: String, id: String = "task") =
            RealCommitTask(id, repository, "parent", "commit", 1, "private query text", listOf("gold"), RealSplit.DEV)

        private fun set(kind: BenchmarkSet, id: String = "task", on: Double = 0.8) = ContextSetResult(
            kind,
            ContextBenchmarkBudgets.values.map { budget ->
                val task = TaskContextResult(
                    id,
                    "private query text",
                    ContextGoldSpec(listOf("gold")),
                    mapOf(OFF to arm(0.7, SemanticStatus.OFF), ON to arm(on, status)),
                    resolvedGoldFiles = listOf("gold"),
                )
                ContextBudgetResult(budget, listOf(task), emptyMap(), false, emptyList(), true)
            },
        )

        private fun reference(
            baselines: List<ContextBenchmarkMethod> = listOf(
                ContextBenchmarkMethod.BM25_RAW,
                ContextBenchmarkMethod.BM25_ENGLISH,
            ),
        ): ContextBenchmarkResult = ContextBenchmarkResult(
            BenchmarkSet.entries.map { kind ->
                val set = set(kind)
                set.copy(
                    budgets = set.budgets.map { row ->
                        row.copy(
                            tasks = row.tasks.map { task ->
                                task.copy(methods = task.methods + baselines.associateWith { arm(0.75) })
                            },
                        )
                    },
                )
            },
            TOKENIZER,
            LocalizationCorpusSummary(),
            ContextMeasurementDefinition(),
            false,
            true,
            true,
            pipeline = pipeline,
        )

        private fun arm(value: Double, semantic: SemanticStatus? = null) = ContextMethodResult(
            ContextScores(
                value, value, null, value, value,
                TokenCount(
                    10,
                ),
                TokenCount(5), emptyList(), emptyList(), emptyList(),
            ),
            ContextPackage("query", ContextLevel.RECOMMENDED, TokenCount(2000), TOKENIZER, TokenCount(10), emptyList()),
            FullFileCoverageCosts(),
            semantic,
        )

        private fun change(record: SemanticContextRecord, transform: (ContextSetResult) -> ContextSetResult) =
            record.copy(original = record.original?.let(transform), paraphrase = record.paraphrase?.let(transform))

        private fun changeTasks(record: SemanticContextRecord, transform: (TaskContextResult) -> TaskContextResult) =
            change(record) { it.copy(budgets = it.budgets.map { row -> row.copy(tasks = row.tasks.map(transform)) }) }
    }
}
