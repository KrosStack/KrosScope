package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import com.intellij.psi.PsiJavaFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.AnalysisWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.OwnedFile
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.SourceSetFiles
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.readAccess
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.FileContext
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.SymbolIdFactory
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs.JavaFileWalker
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs.JavaReferences
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs.JavaResolver
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs.JavaSyntheticMembers
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs.JavaTargets
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinAliases
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinCallLikes
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinDeclarationLinks
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinFileWalker
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinReferences
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinResolver
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinSyntheticMembers
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinTargets
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs.KotlinWorkspaceTargets
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries.KotlinLibraryIds
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtFile

/**
 * The relationship pass: resolves the references of the Kotlin and Java files of a source set, one file at a time and
 * one compiler session scope per file, and turns them into relationship facts. A file the compiler fails on keeps the
 * relationships found before the failure and gets a diagnostic.
 */
internal class RelationshipPass {
    fun factsOf(workspace: AnalysisWorkspace, sourceSet: SourceSetFiles, collisions: IdentityCollisions): List<Fact> {
        val factory = SymbolIdFactory(workspace.owners)
        return sourceSet.files.flatMap { factsOf(it, factory, collisions, sourceSet.hasClasspath) }
    }

    private fun factsOf(
        owned: OwnedFile,
        factory: SymbolIdFactory,
        collisions: IdentityCollisions,
        hasClasspath: Boolean,
    ): List<Fact> {
        val context = FileContext(owned.owner, owned.psi)
        val fileRef = owned.owner.artifact?.let { NodeRef.OfArtifact(it) } ?: return emptyList()
        val reporter = ReferenceReporter(context, hasClasspath, collisions)
        val ids = WorkspaceIds(factory, owned.owner.moduleName, collisions)
        val outcome = runCatching { resolve(owned, ids, reporter, fileRef) }
        val relationships = reporter.relationships().map { Fact.OfRelationship(it) }
        return relationships + diagnosticsOf(context, outcome)
    }

    private fun resolve(owned: OwnedFile, ids: WorkspaceIds, reporter: ReferenceReporter, fileRef: NodeRef): Int =
        when (val file = owned.psi) {
            is KtFile -> resolveKotlin(file, ids, reporter, fileRef)
            is PsiJavaFile -> readAccess { resolveJava(file, ids, reporter, fileRef) }
            else -> 0
        }

    private fun resolveKotlin(file: KtFile, ids: WorkspaceIds, reporter: ReferenceReporter, fileRef: NodeRef): Int =
        analyze(file) {
            val resolver = KotlinResolver(this)
            val targets = KotlinTargets(this, KotlinWorkspaceTargets(ids), KotlinLibraryIds(this))
            val synthetic = KotlinSyntheticMembers(resolver, ids)
            val references = KotlinReferences(resolver, targets, reporter, KotlinAliases(this), synthetic)
            val walker = KotlinFileWalker(
                ids,
                references,
                KotlinCallLikes(resolver, targets, reporter),
                KotlinDeclarationLinks(resolver, targets, reporter),
            )
            walker.walk(file, fileRef)
            resolver.failures
        }

    private fun resolveJava(file: PsiJavaFile, ids: WorkspaceIds, reporter: ReferenceReporter, fileRef: NodeRef): Int {
        val resolver = JavaResolver()
        val references = JavaReferences(resolver, JavaTargets(ids), reporter, JavaSyntheticMembers(resolver, ids))
        JavaFileWalker(ids, references).walk(file, fileRef)
        return resolver.failures
    }

    private fun diagnosticsOf(context: FileContext, outcome: Result<Int>): List<Fact> {
        val failure = outcome.exceptionOrNull()
        if (failure is Error && failure !is StackOverflowError) throw failure
        failure?.printStackTrace()
        val skipped = outcome.getOrDefault(0)
        val diagnostic = when {
            failure != null -> {
                val message = "Resolving the references failed with ${failure.javaClass.simpleName}"
                diagnostic(context, DiagnosticSeverity.ERROR, FAILED, message)
            }

            skipped > 0 -> {
                val message = "$skipped references were left out because the compiler failed on them"
                diagnostic(context, DiagnosticSeverity.WARNING, REFERENCES_SKIPPED, message)
            }

            else -> null
        }
        return listOfNotNull(diagnostic).map { Fact.OfDiagnostic(it) }
    }

    private fun diagnostic(context: FileContext, severity: DiagnosticSeverity, code: String, message: String) =
        Diagnostic(severity, ReasonCode(code), message, context.startOfFile())

    private companion object {
        const val FAILED = "kotlin.file-analysis-failed"
        const val REFERENCES_SKIPPED = "kotlin.reference-resolution-failed"
    }
}
