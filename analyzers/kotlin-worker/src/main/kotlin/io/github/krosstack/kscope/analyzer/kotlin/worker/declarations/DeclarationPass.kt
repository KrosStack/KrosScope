package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.KotlinAnnotationResolver
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.OwnedFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.SourceSetFiles
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.readAccess
import io.github.krosstack.kscope.analyzer.kotlin.worker.plan.PlanProblem
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.TextRange
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

private class FileResult(val symbols: List<Symbol>, val diagnostics: List<Diagnostic>)

private fun collisionDiagnostic(symbol: Symbol, first: Symbol): Diagnostic {
    val firstSite = first.declaration?.let { "${it.artifact.path.value}:${it.range.startLine}" } ?: "another file"
    return Diagnostic(
        DiagnosticSeverity.WARNING,
        ReasonCode("identity.collision"),
        "${symbol.qualifiedName} has the same identity as the declaration at $firstSite and is not recorded",
        symbol.declaration,
    )
}

/**
 * The declaration pass: one fact batch per requested source set, holding the symbols read from the syntax trees of its
 * Kotlin and Java files and the diagnostics about files that could not be read completely.
 */
internal class DeclarationPass(
    private val producer: ProducerRef,
    qualify: (KtAnnotationEntry) -> String? = KotlinAnnotationResolver()::qualifiedName,
) {
    private val kotlin = KotlinDeclarations(qualify)
    private val java = JavaDeclarations()

    fun batchesOf(workspace: AnalysisWorkspace): List<FactBatch> =
        workspace.sourceSets.filter { it.source.emitFactsFor }.map { sourceSet ->
            FactBatch(
                producer,
                OwnershipUnit(OwnershipKind.SOURCE_SET, sourceSet.source.sourceSet.value),
                sourceSet.source.fingerprint,
                factsOf(workspace, sourceSet),
            )
        }

    /** The symbols of the source set, then the diagnostics about reading them. */
    fun factsOf(workspace: AnalysisWorkspace, sourceSet: SourceSetFiles): List<Fact> =
        factsOf(workspace.planProblems, sourceSet)

    fun factsOf(planProblems: List<PlanProblem>, sourceSet: SourceSetFiles): List<Fact> {
        val results = sourceSet.files.map(::extractFile)
        val (symbols, collisions) = mergeUniqueIds(results.flatMap { it.symbols })
        val diagnostics = results.flatMap { it.diagnostics } + collisions +
            missingFileDiagnostics(sourceSet) + planDiagnostics(planProblems, sourceSet)
        return symbols.map { Fact.OfSymbol(it) } + diagnostics.map { Fact.OfDiagnostic(it) }
    }

    /** The ids that more than one declaration of the source sets carries, of which a pass records only the first. */
    fun collidedIdsOf(sourceSets: List<SourceSetFiles>): Set<SymbolId> = sourceSets.flatMapTo(HashSet()) { sourceSet ->
        val seen = HashSet<SymbolId>()
        sourceSet.files.asSequence().flatMap { extractFile(it).symbols }.map { it.id }.filterNot(seen::add).toList()
    }

    private fun extractFile(owned: OwnedFile): FileResult {
        val context = FileContext(owned.owner, owned.psi)
        val symbols = mutableListOf<Symbol>()
        val failures = runCatching { readAccess { extractInto(owned, context, symbols) } }
            .exceptionOrNull()?.let { listOf(failureDiagnostic(context, it)) }.orEmpty()
        return FileResult(propagateTestFlag(symbols), failures + syntaxDiagnostics(context))
    }

    private fun extractInto(owned: OwnedFile, context: FileContext, out: MutableList<Symbol>) {
        when (val file = owned.psi) {
            is KtFile -> kotlin.extract(file, context, out)
            is PsiJavaFile -> java.extract(file, context, out)
        }
    }

    private fun mergeUniqueIds(symbols: List<Symbol>): Pair<List<Symbol>, List<Diagnostic>> {
        val kept = LinkedHashMap<SymbolId, Symbol>()
        val collisions = mutableListOf<Diagnostic>()
        symbols.forEach { symbol ->
            val first = kept.putIfAbsent(symbol.id, symbol)
            if (first != null) collisions += collisionDiagnostic(symbol, first)
        }
        return kept.values.toList() to collisions
    }

    private fun failureDiagnostic(context: FileContext, failure: Throwable): Diagnostic {
        if (failure is Error && failure !is StackOverflowError) throw failure
        failure.printStackTrace()
        return Diagnostic(
            DiagnosticSeverity.ERROR,
            ReasonCode("kotlin.file-analysis-failed"),
            "Reading the declarations failed with ${failure.javaClass.simpleName}",
            context.startOfFile(),
        )
    }

    private fun syntaxDiagnostics(context: FileContext): List<Diagnostic> {
        val errors = PsiTreeUtil.findChildrenOfType(context.file, PsiErrorElement::class.java).toList()
        val first = errors.firstOrNull() ?: return emptyList()
        val language = if (context.file is PsiJavaFile) "java" else "kotlin"
        val count = if (errors.size == 1) "1 syntax error" else "${errors.size} syntax errors"
        return listOf(
            Diagnostic(
                DiagnosticSeverity.WARNING,
                ReasonCode("$language.syntax-error"),
                "$count, the first: ${first.errorDescription}",
                context.location(first.textRange.startOffset until first.textRange.endOffset),
            ),
        )
    }

    private fun missingFileDiagnostics(sourceSet: SourceSetFiles): List<Diagnostic> = sourceSet.missing.map { file ->
        Diagnostic(
            DiagnosticSeverity.ERROR,
            ReasonCode("kotlin.file-analysis-failed"),
            "The file is missing or is not a Kotlin or Java source file",
            SourceLocation(file.ref, TextRange(1, 1, 1, 1)),
        )
    }

    private fun planDiagnostics(planProblems: List<PlanProblem>, sourceSet: SourceSetFiles): List<Diagnostic> =
        planProblems.filter { it.module == sourceSet.moduleName }.map {
            Diagnostic(
                DiagnosticSeverity.WARNING,
                ReasonCode(it.kind.code),
                "Module ${it.module} refers to ${it.referenced}, which cannot be used as a dependency",
                null,
            )
        }
}
