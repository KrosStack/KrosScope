package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.nio.file.Files
import java.nio.file.Path

public object LocalizationBenchmark {
    private const val TEST_PRECISION_TARGET = 0.5
    private const val TEST_RECALL_TARGET = 0.8
    private const val SYMBOL_RECALL_TARGET = 0.5
    private val json = Json { ignoreUnknownKeys = true }

    public fun loadTasks(directory: Path): List<LocalizationTask> = Files.list(directory).use { files ->
        files.filter { Files.isRegularFile(it) }.sorted().map { path ->
            json.decodeFromString<LocalizationTask>(Files.readString(path))
        }.toList().sortedBy { it.id }
    }

    public fun loadStatements(path: Path): List<LocalizationStatement> =
        readStatements(if (Files.isDirectory(path)) path.resolve("statements.json") else path).sortedBy { it.id }

    public fun run(
        scope: KrosScope,
        workspace: Path,
        tasks: List<LocalizationTask>,
        heldOut: List<LocalizationStatement>? = null,
    ): LocalizationSuiteResult {
        val sets = linkedMapOf(BenchmarkSet.ORIGINAL to tasks.sortedBy { it.id })
        val errors = heldOut?.let { LocalizationStatements.validationErrors(tasks, it) }.orEmpty()
        if (heldOut != null && errors.isEmpty()) sets[BenchmarkSet.HELD_OUT] = pairStatements(tasks, heldOut)
        val artifacts = scope.graph().artifacts()
        val names = WorkspaceNames.of(scope.graph())
        val existing = artifacts.filter { it.generated == null }.map { names.of(it.ref) }.toSet()
        val corpus = LocalizationCorpus.load(workspace, artifacts, names)
        val structure = scope.graph().structure()
        val results = PlainTextSearch(corpus.documents).use { raw ->
            PlainTextSearch(corpus.documents, removeEnglishStopWords = true).use { english ->
                sets.map { (kind, entries) ->
                    val evaluator = LocalizationEvaluator(scope, structure, existing, raw, english)
                    aggregate(entries.map(evaluator::evaluate), kind)
                }
            }
        }
        return combine(results, corpus.summary, errors)
    }

    internal fun pairStatements(
        tasks: List<LocalizationTask>,
        statements: List<LocalizationStatement>,
    ): List<LocalizationTask> {
        require(LocalizationStatements.validationErrors(tasks, statements).isEmpty()) { "Invalid held-out pairing." }
        val paired = statements.associateBy { it.id }
        return tasks.sortedBy { it.id }.map { it.copy(statement = paired.getValue(it.id).statement) }
    }

    internal fun aggregate(
        tasks: List<TaskLocalization>,
        kind: BenchmarkSet = BenchmarkSet.ORIGINAL,
    ): LocalizationSetResult {
        val methods = BenchmarkMethod.entries.associateWith { method ->
            LocalizationMetrics.mean(tasks.map { it.methods.getValue(method).scores })
        }
        val strongest = LocalizationComparisons.strongestLexical(methods)
        val strongestBm25 = LocalizationComparisons.strongestBm25(methods)
        val kscope = methods.getValue(BenchmarkMethod.FULL)
        val valid = tasks.isNotEmpty() && tasks.all { it.validGold && it.missingGoldFiles.isEmpty() } &&
            tasks.map { it.id }.distinct().size == tasks.size
        val passed = valid && kscope.recall5 > strongestBm25.recall5 && kscope.recall10 > strongestBm25.recall10 &&
            kscope.ndcg10 > strongestBm25.ndcg10
        val testsPassed = valid && reaches(kscope.analysis?.testPrecision, TEST_PRECISION_TARGET) &&
            reaches(kscope.analysis?.testRecall, TEST_RECALL_TARGET)
        val symbolsPassed = valid && reaches(kscope.analysis?.symbolRecall10, SYMBOL_RECALL_TARGET)
        return LocalizationSetResult(
            kind, tasks, methods, strongest, passed, testsPassed, symbolsPassed, valid,
            strongestBm25, LocalizationComparisons.lexical(tasks),
        )
    }

    internal fun combine(
        sets: List<LocalizationSetResult>,
        corpus: LocalizationCorpusSummary = LocalizationCorpusSummary(),
        validationErrors: List<String> = emptyList(),
    ): LocalizationSuiteResult {
        val complete = sets.map { it.kind } == BenchmarkSet.entries
        val valid = sets.isNotEmpty() && sets.all { it.valid } && validationErrors.isEmpty()
        return LocalizationSuiteResult(
            sets,
            corpus,
            valid && complete && sets.all { it.gatePassed },
            valid && complete && sets.all { it.testSelectionPassed && it.symbolRecallPassed },
            valid,
            complete,
            validationErrors,
            LocalizationGatePolicy.TWO_TIER,
        )
    }

    public fun reclassify(result: LocalizationSuiteResult): LocalizationSuiteResult = combine(
        result.sets.map { aggregate(it.tasks, it.kind) },
        result.corpus,
        result.validationErrors,
    )

    private fun reaches(score: Double?, target: Double): Boolean = score != null && score >= target

    private fun readStatements(path: Path): List<LocalizationStatement> {
        val content = json.parseToJsonElement(Files.readString(path))
        return if (content is JsonArray) {
            json.decodeFromJsonElement(content)
        } else {
            listOf(json.decodeFromJsonElement(content))
        }
    }
}

private class LocalizationEvaluator(
    private val scope: KrosScope,
    private val structure: WorkspaceStructure,
    private val existing: Set<String>,
    private val raw: PlainTextSearch,
    private val english: PlainTextSearch,
) {
    private val names = WorkspaceNames.of(scope.graph())

    fun evaluate(task: LocalizationTask): TaskLocalization {
        val methods = BenchmarkMethod.entries.associateWith { method ->
            if (method.stage == null) baseline(task, method) else retrieval(task, method)
        }
        val files = task.mustEdit + task.mustRead + task.symbols.map { it.file } +
            task.relevantTests.orEmpty().map { it.file }
        return TaskLocalization(
            task.id,
            task.statement,
            methods,
            files.filter { it !in existing }.distinct().sorted(),
            task.id.isNotBlank() && task.statement.isNotBlank() && task.mustEdit.isNotEmpty(),
        )
    }

    private fun retrieval(task: LocalizationTask, method: BenchmarkMethod): LocalizationMethodResult {
        val result = scope.analyze(task.statement, requireNotNull(method.stage), includeContextEstimates = false)
        val ranked = result.files.map { names.of(it.artifact) }
        val edits = result.files.filter { it.role == FileRole.EDIT }.map { names.of(it.artifact) }
        val scores = LocalizationMetrics.score(ranked, task, edits).copy(
            analysis = LocalizationAnalysisMetrics.score(result, task, structure, names),
        )
        return LocalizationMethodResult(
            scores,
            ranked,
            edits,
            predictedSymbols = result.symbols.take(SYMBOL_SNAPSHOT_LIMIT),
            selectedTests = result.tests,
            predictedScope = predictedScope(result),
        )
    }

    private fun predictedScope(result: AnalysisResult): LocalizationGoldScope = LocalizationGoldScope(
        modules = structure.modules.filter { it.id in result.scope.modules }.map { it.path }.distinct().sorted(),
        sourceSets = structure.sourceSets.filter {
            it.id in result.scope.sourceSets
        }.map { it.name }.distinct().sorted(),
        variants = result.scope.variants.distinct().sorted(),
    )

    private fun baseline(task: LocalizationTask, method: BenchmarkMethod): LocalizationMethodResult {
        val ranked = when (method) {
            BenchmarkMethod.BM25_RAW -> raw.search(task.statement)

            BenchmarkMethod.BM25_ENGLISH -> english.search(task.statement)

            BenchmarkMethod.PATH_ORDER -> existing.sorted()

            BenchmarkMethod.LEXICAL, BenchmarkMethod.GRAPH, BenchmarkMethod.PROJECTION,
            BenchmarkMethod.CATEGORY, BenchmarkMethod.FULL,
            ->
                error("Retrieval stages must be evaluated through the service.")
        }
        val selected = ranked.take(BASELINE_SELECTION)
        return LocalizationMethodResult(LocalizationMetrics.score(ranked, task, selected), ranked, selected)
    }

    private companion object {
        const val BASELINE_SELECTION = 10
        const val SYMBOL_SNAPSHOT_LIMIT = 10
    }
}
