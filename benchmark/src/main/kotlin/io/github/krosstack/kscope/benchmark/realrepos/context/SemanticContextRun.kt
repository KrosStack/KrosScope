package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkTask
import io.github.krosstack.kscope.benchmark.context.ContextGoldSpec
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkIdentity
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkSelection
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.context.semantic.ContextPipelineIdentityFactory
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunOptions
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunStorage
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealRunValidation
import io.github.krosstack.kscope.benchmark.realrepos.measurement.RealSnapshot
import io.github.krosstack.kscope.benchmark.realrepos.measurement.canonicalFuturePath
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.semantic.semanticNotice
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.nio.file.Path

internal class SemanticContextRun(
    private val options: SemanticContextOptions,
    private val open: (Path, OpenOptions) -> KrosScope = KrosScope::open,
    private val readIdentity: (KrosScope, Path) -> ContextPipelineIdentity = ContextPipelineIdentityFactory::read,
    private val recipe: () -> ContentHash = ContextPipelineIdentityFactory::recipe,
) {
    fun run(manifest: RealRepositoryManifest): List<SemanticContextRecord> = run(manifest) { it }

    fun <T> run(manifest: RealRepositoryManifest, publish: (List<SemanticContextRecord>) -> T): T {
        validate(manifest)
        val tasks = options.tasks(manifest)
        require(tasks.isNotEmpty() && tasks.all { it.workspace.isEmpty() && it.goldFiles.isNotEmpty() })
        val queries = options.queries(tasks)
        val globalRecipe = recipe()
        val checkpoint = SemanticContextCheckpoint(options.output)
        return checkpoint.locked(options.scratch) {
            val binding = checkpoint.bind(binding(manifest, queries, globalRecipe))
            val records = mutableListOf<SemanticContextRecord>()
            for (task in tasks) {
                val previous = checkpoint.load(task, binding)
                val record = previous ?: execute(manifest, task, queries[task.id], binding).also {
                    check(recipe() == globalRecipe) { "Semantic context recipe changed; record not published." }
                    checkpoint.save(it)
                }
                records += record
                if (record.failure != null) break
            }
            check(recipe() == globalRecipe) { "Semantic context recipe changed during resume." }
            publish(records)
        }
    }

    private fun binding(manifest: RealRepositoryManifest, queries: Map<String, String?>, globalRecipe: ContentHash) =
        buildJsonObject {
            put("manifest", KscopeJson.encodeToJsonElement(manifest))
            put("split", options.split.name)
            put("queries", KscopeJson.encodeToJsonElement(queries))
            put("recipe", globalRecipe.value)
            put(
                "arms",
                KscopeJson.encodeToJsonElement(
                    SemanticContextOptions.METHODS.map {
                        listOf(it.name, it.stage?.name, it.ml.name)
                    },
                ),
            )
            put("budgets", KscopeJson.encodeToJsonElement(ContextBenchmarkBudgets.values))
            put("level", ContextLevel.RECOMMENDED.name)
            put("estimator", ContextBenchmarkOptions().estimator.id.value)
            put("caller", options.flags.getValue("--checkpoint-key"))
        }

    private fun validate(manifest: RealRepositoryManifest) {
        val key = options.flags.getValue("--checkpoint-key")
        RealRunValidation.validate(manifest, RealRunOptions(options.output, key))
        RealRunValidation.validate(manifest, RealRunOptions(options.scratch, key))
        require(options.repositories.all { id -> manifest.repositories.any { it.id == id } })
        val output = canonicalFuturePath(options.output)
        val scratch = canonicalFuturePath(options.scratch)
        require(!scratch.startsWith(output) && !output.startsWith(scratch)) { "Output and scratch must be separate." }
    }

    private fun execute(
        manifest: RealRepositoryManifest,
        task: RealCommitTask,
        query: String?,
        binding: ContentHash,
    ): SemanticContextRecord = runCatching {
        val storage = RealRunStorage(options.output)
        val scratch = RealRunStorage(options.scratch)
        val repository = manifest.repositories.single { it.id == task.repository }
        val snapshot = RealSnapshot(repository, storage, scratch.scratch(task.repository))
        snapshot.prepare()
        snapshot.checkout(task.parent)
        val index = storage.index(task.repository)
        val openOptions = measuredOpenOptions(index).copy(semanticIndexing = SemanticIndexingMode.FOREGROUND)
        open(snapshot.workspace, openOptions).use { scope ->
            val scan = scope.scan(rebuild = !scope.isIndexed())
            check(scan.diagnostics.errors == 0) { "Parent scan failed: ${scan.diagnostics.errors} errors." }
            check(scope.semanticNotice() == null) { "Foreground indexing failed: ${scope.semanticNotice()}" }
            val pipeline = readIdentity(scope, index)
            checkNotNull(pipeline.generation) { "Semantic ON requires an admitted parent generation." }
            val inventory = ContextBenchmarkIdentity.fingerprint(
                KscopeJson.encodeToJsonElement(scope.graph().artifacts()),
            )
            score(scope, snapshot.workspace, query, SemanticContextRecord(task, binding, pipeline, inventory))
        }
    }.getOrElse {
        if (it is Error || it is InterruptedException) throw it
        SemanticContextRecord(task, binding, failure = "${it.javaClass.simpleName}: ${it.message}")
    }

    private fun score(
        scope: KrosScope,
        workspace: Path,
        query: String?,
        provenance: SemanticContextRecord,
    ): SemanticContextRecord {
        val measurement = ContextBenchmarkOptions(
            selection = ContextBenchmarkSelection(SemanticContextOptions.METHODS, provenance.pipeline),
        )
        val task = provenance.task
        val request = ContextBenchmarkTask(task.id, task.statement, ContextGoldSpec(task.goldFiles))
        fun measure(statement: String) = ContextBenchmark.evaluate(
            scope,
            workspace,
            listOf(request.copy(statement = statement)),
            measurement,
            acceptsAbsentGold = true,
        )
        val original = measure(task.statement)
        val paraphrase = query?.let { measure(it).copy(kind = BenchmarkSet.HELD_OUT) }
        val record = provenance.copy(
            original = original,
            paraphrase = paraphrase,
            unresolved = if (query == null) SemanticContextRecord.UNRESOLVED_PARAPHRASE else null,
        )
        return if (record.valid(original) && (query == null || record.valid(paraphrase))) {
            record
        } else {
            record.copy(failure = "Context arms failed source or complete semantic coverage; inspect request outcomes.")
        }
    }
}
