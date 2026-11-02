package io.github.krosstack.kscope.benchmark.agent.workspace

import io.github.krosstack.kscope.benchmark.agent.Arm
import io.github.krosstack.kscope.benchmark.agent.ContextRecord
import io.github.krosstack.kscope.benchmark.context.ContextFilePacker
import io.github.krosstack.kscope.benchmark.context.SourceDocument
import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpus
import io.github.krosstack.kscope.benchmark.localization.PlainTextSearch
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.context
import io.github.krosstack.kscope.spi.context.TokenEstimator
import java.nio.file.Path

internal class PreparedContext(val markdown: String?, val record: ContextRecord)

internal object PromptTemplate {
    const val VERSION = "p8-1"

    fun render(statement: String, context: String?): String = buildString {
        appendLine("You are working in the root directory of a software project. Complete the task below by editing")
        appendLine("the project's files in place. Nobody can answer questions during the run, so decide and act.")
        appendLine("The project is built and its tests are run after you finish. Stop when the change is complete.")
        appendLine()
        appendLine("## Task")
        appendLine()
        appendLine(statement.trim())
        if (context != null) {
            appendLine()
            appendLine("## Context")
            appendLine()
            appendLine("This source context was collected automatically for the task. It can be incomplete, and it may")
            appendLine("contain files that do not matter.")
            appendLine()
            appendLine(context.trim())
        }
    }
}

internal class ContextPreparer(private val budget: Int, private val estimator: TokenEstimator) {
    fun prepare(
        arm: Arm,
        statement: String,
        workspace: Path,
        indexDirectory: Path,
        gold: Set<String>,
    ): PreparedContext {
        if (!arm.usesContext) {
            return PreparedContext(null, ContextRecord(budget = budget, estimator = estimator.id.value))
        }
        val options = measuredOpenOptions(indexDirectory)
        return KrosScope.open(workspace, options).use { scope ->
            val indexStart = System.nanoTime()
            val scan = scope.scan()
            check(scan.diagnostics.errors == 0) { "The base state scans with ${scan.diagnostics.errors} errors." }
            val indexMs = elapsedMs(indexStart)
            val contextStart = System.nanoTime()
            val pack = if (arm == Arm.C) compile(scope, statement) else bm25(scope, workspace, statement)
            val contextMs = elapsedMs(contextStart)
            describe(pack, indexMs, contextMs, gold)
        }
    }

    private fun compile(scope: KrosScope, statement: String): ContextPackage =
        scope.context(statement, TokenCount(budget), ContextLevel.RECOMMENDED, estimator)

    private fun bm25(scope: KrosScope, workspace: Path, statement: String): ContextPackage {
        val artifacts = scope.graph().artifacts()
        val names = WorkspaceNames.of(scope.graph())
        val corpus = LocalizationCorpus.load(workspace, artifacts, names)
        val documents: Map<ArtifactRef, SourceDocument> = artifacts.mapNotNull { artifact ->
            corpus.documents[names.of(artifact.ref)]?.let { artifact.ref to SourceDocument(artifact.ref, it) }
        }.toMap()
        return PlainTextSearch(corpus.documents, removeEnglishStopWords = true).use { english ->
            ContextFilePacker(documents, estimator, names)
                .pack(statement, english.search(statement), TokenCount(budget))
        }
    }

    private fun describe(pack: ContextPackage, indexMs: Long, contextMs: Long, gold: Set<String>): PreparedContext {
        val files = pack.fragments.map { it.artifact.path.value }.distinct().sorted()
        val record = ContextRecord(
            tokens = estimator.count(pack.markdown).value,
            files = files,
            goldFilesInContext = files.count { it in gold },
            indexMs = indexMs,
            contextMs = contextMs,
            budget = budget,
            estimator = estimator.id.value,
        )
        return PreparedContext(pack.markdown, record)
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
