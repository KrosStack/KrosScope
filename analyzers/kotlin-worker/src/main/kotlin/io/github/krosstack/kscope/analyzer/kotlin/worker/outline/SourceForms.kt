package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.LineIndex
import io.github.krosstack.kscope.analyzer.kotlin.worker.syntax.literalValue
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression

private val scopeFunctions = setOf("run", "apply")

/** What differs in reading the body of a function from reading a script; see [OutlineReader]. */
internal class SourceForms(
    private val lines: LineIndex,
    private val inliner: FunctionInliner,
    private val scope: ReadScope,
) {
    /** The statements of `with(x) { }`, `x.run { }` and `x.apply { }` when `x` is a subject of the scope. */
    fun unwrap(statement: KtExpression): List<KtExpression>? {
        val (subject, lambda) = scopeCall(statement) ?: return null
        return lambda.bodyExpression?.statements.takeIf { subject in scope.subjects }
    }

    fun controlCall(statement: KtExpression, read: (List<KtExpression>) -> Body): ScriptCall? {
        val branches = controlBranches(statement) ?: return null
        val body = read(branches.flatMap { if (it is KtBlockExpression) it.statements else listOf(it) })
        val keyword = controlKeyword(statement)
        return ScriptCall(keyword, null, emptyList(), body.calls, body.assignments, rangeOf(statement))
    }

    fun valueCall(property: KtProperty, initializer: (KtExpression) -> ScriptCall?): ScriptCall? {
        val name = property.name?.takeUnless { property.hasDelegate() } ?: return null
        val value = property.initializer
        val argument = value?.let { ScriptArgument(null, literalValue(it), it.text) }
        return valueBinding(name, property.typeReference?.text, argument, rangeOf(property))
            .copy(body = listOfNotNull(value?.let(initializer)))
    }

    fun inlined(call: ScriptCall): ScriptCall = inliner.inline(call, scope)

    private fun scopeCall(statement: KtExpression): Pair<String, KtLambdaExpression>? = when (statement) {
        is KtCallExpression -> withCall(statement)
        is KtQualifiedExpression -> runCall(statement)
        else -> null
    }

    private fun withCall(call: KtCallExpression): Pair<String, KtLambdaExpression>? {
        val lambda = call.lambdaArguments.singleOrNull()?.getLambdaExpression()
        val subject = call.valueArguments.firstOrNull()?.getArgumentExpression()?.text
        val isWith = call.calleeExpression?.text == "with"
        return if (isWith && subject != null && lambda != null) subject to lambda else null
    }

    private fun runCall(call: KtQualifiedExpression): Pair<String, KtLambdaExpression>? {
        val selector = call.selectorExpression as? KtCallExpression
        val lambda = selector?.lambdaArguments?.singleOrNull()?.getLambdaExpression()
        val isScopeFunction = selector?.calleeExpression?.text in scopeFunctions
        return if (isScopeFunction && lambda != null) call.receiverExpression.text to lambda else null
    }

    private fun rangeOf(expression: KtExpression) =
        lines.range(expression.textRange.startOffset, expression.textRange.endOffset)
}
