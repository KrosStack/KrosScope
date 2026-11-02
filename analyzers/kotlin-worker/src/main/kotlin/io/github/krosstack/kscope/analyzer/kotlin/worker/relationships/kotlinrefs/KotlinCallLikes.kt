package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import com.intellij.psi.PsiElement
import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.offsets
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Attempt
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceReporter
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.ReferenceSite
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Shape
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

private val iterationShape = Shape.of(RelationKind.CALLS, "iteration")
private val delegateShape = Shape.of(RelationKind.CALLS, "delegate")
private val destructuringShape = Shape.of(RelationKind.CALLS, "destructuring")
private val equalityTokens = setOf(KtTokens.EQEQ, KtTokens.EXCLEQ)

/**
 * Reports the calls that Kotlin syntax makes without a call expression: operators, indexing, iteration, delegation,
 * destructuring and the constructor calls of supertype lists and secondary constructors. Calls the language makes
 * implicitly are reported when they resolve; a failure there is already visible on the expression that caused it.
 */
internal class KotlinCallLikes(
    private val resolver: KotlinResolver,
    private val targets: KotlinTargets,
    private val reporter: ReferenceReporter,
) {
    /** Returns whether [element] is one of these constructs. */
    fun visit(element: PsiElement, from: NodeRef): Boolean {
        when (element) {
            is KtOperationReferenceExpression -> operation(element, from)
            is KtSuperTypeCallEntry -> superTypeCall(element, from)
            is KtConstructorDelegationCall -> delegationCall(element, from)
            is KtArrayAccessExpression -> arrayAccess(element, from)
            is KtForExpression -> iteration(element, from)
            is KtPropertyDelegate -> delegate(element, from)
            is KtDestructuringDeclarationEntry -> destructuring(element, from)
            else -> return false
        }
        return true
    }

    private fun operation(operation: KtOperationReferenceExpression, from: NodeRef) {
        val isInfixCall = operation.operationSignTokenType == null
        if (!isInfixCall && !operation.isConventionOperator()) return
        val attempt = resolver.symbolsOf(operation)
        val reported = if (isInfixCall) attempt else withoutGeneratedEquals(operation, successOnly(attempt))
        reportAttempt(from, operation, operation.text, Shape.call, reported)
    }

    // `==` on a data or value class calls an `equals` that the compiler made; nothing in the source declares it. An
    // `equals` that a class declares itself is a call as any other.
    private fun withoutGeneratedEquals(operation: KtOperationReferenceExpression, attempt: Attempt<KaSymbol>) =
        if (operation.operationSignTokenType in equalityTokens && attempt is Attempt.Resolved) {
            val declared = attempt.symbols.filterNot { it.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED }
            if (declared.isEmpty()) Attempt.Unavailable else Attempt.Resolved(declared)
        } else {
            attempt
        }

    private fun superTypeCall(entry: KtSuperTypeCallEntry, from: NodeRef) {
        val site: PsiElement = entry.getParentOfType<KtEnumEntry>(true)?.nameIdentifier ?: entry.calleeExpression
        reportAttempt(from, site, site.text, Shape.call, resolver.symbolsOf(entry))
    }

    private fun delegationCall(call: KtConstructorDelegationCall, from: NodeRef) {
        val callee: PsiElement = call.calleeExpression ?: call
        reportAttempt(from, callee, callee.text, Shape.call, resolver.symbolsOf(call))
    }

    private fun delegate(delegate: KtPropertyDelegate, from: NodeRef) =
        reportAttempt(from, delegate.byKeywordNode.psi, "by", delegateShape, resolver.delegateFunctions(delegate))

    private fun iteration(loop: KtForExpression, from: NodeRef) =
        reportAttempt(from, loop.loopRange, "in", iterationShape, resolver.iterationFunctions(loop))

    private fun arrayAccess(access: KtArrayAccessExpression, from: NodeRef) =
        reportAttempt(from, access.indicesNode, "get", Shape.call, successOnly(resolver.symbolsOf(access)))

    private fun destructuring(entry: KtDestructuringDeclarationEntry, from: NodeRef) {
        val attempt = successOnly(resolver.symbolsOf(entry))
        reportAttempt(from, entry.nameIdentifier ?: entry, entry.text, destructuringShape, attempt)
    }

    private fun reportAttempt(
        from: NodeRef,
        site: PsiElement?,
        hint: String,
        shape: Shape,
        attempt: Attempt<KaSymbol>,
    ) {
        val range = site?.offsets() ?: return
        reporter.report(ReferenceSite(from, range, hint), shape, attempt) { targets.map(it, Access.NONE, false) }
    }

    private fun successOnly(attempt: Attempt<KaSymbol>): Attempt<KaSymbol> =
        if (attempt is Attempt.Resolved) attempt else Attempt.Unavailable
}
