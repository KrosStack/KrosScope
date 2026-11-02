package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.LineIndex
import io.github.krosstack.kscope.analyzer.kotlin.worker.syntax.literalValue
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** Reads call-shaped statements: plain, qualified, infix, bare names and delegated properties. */
internal class CallReader(private val lines: LineIndex, private val readLambdaBody: (List<KtExpression>) -> Body) {
    /** [statement] is the whole statement; it gives the range, while [expression] is the part being read. */
    fun callOf(expression: KtExpression, statement: KtExpression): ScriptCall? = when (expression) {
        is KtParenthesizedExpression -> expression.expression?.let { callOf(it, statement) }
        is KtBinaryExpression -> infixCall(expression, statement)
        is KtQualifiedExpression -> qualifiedCall(expression, statement)
        is KtCallExpression -> plainCall(expression, null, statement)
        is KtNameReferenceExpression -> bareName(expression, statement)
        else -> null
    }

    fun delegatedCall(property: KtProperty): ScriptCall? {
        val call = property.delegateExpression?.let { callOf(it, property) }
        return property.name?.let { name -> call?.withLeadingLiteral(name) }
    }

    private fun ScriptCall.withLeadingLiteral(value: String) =
        copy(arguments = listOf(ScriptArgument(null, value, value)) + arguments)

    private fun infixCall(expression: KtBinaryExpression, statement: KtExpression): ScriptCall? {
        val chain = generateSequence(expression) { it.left as? KtBinaryExpression }.takeWhile(::isInfix).toList()
        val call = chain.lastOrNull()?.left?.let { callOf(it, statement) } ?: return null
        val modifiers = chain.asReversed().mapNotNull { operation ->
            operation.right?.let { operation.operationReference.text to it.text }
        }
        return call.copy(assignments = modifiers.toMap() + call.assignments)
    }

    private fun isInfix(expression: KtBinaryExpression): Boolean = expression.operationToken == KtTokens.IDENTIFIER

    private fun qualifiedCall(expression: KtQualifiedExpression, statement: KtExpression): ScriptCall? =
        (expression.selectorExpression as? KtCallExpression)
            ?.let { plainCall(it, expression.receiverExpression.text, statement) }

    private fun bareName(expression: KtNameReferenceExpression, statement: KtExpression) =
        ScriptCall(expression.getReferencedName(), null, emptyList(), emptyList(), emptyMap(), rangeOf(statement))

    private fun plainCall(call: KtCallExpression, receiver: String?, statement: KtExpression): ScriptCall? {
        val name = calleeName(call) ?: return null
        val lambdas = call.valueArguments.mapNotNull { it.getArgumentExpression() as? KtLambdaExpression }
        val body = readLambdaBody(lambdas.flatMap { it.bodyExpression?.statements.orEmpty() })
        val arguments = call.valueArguments.filterNot { it.getArgumentExpression() is KtLambdaExpression }
            .mapNotNull { argument ->
                argument.getArgumentExpression()?.let { argumentOf(argument.getArgumentName()?.asName?.asString(), it) }
            }
        val typeArguments = call.typeArguments.mapNotNull { it.typeReference?.text }
        return ScriptCall(name, receiver, arguments, body.calls, body.assignments, rangeOf(statement), typeArguments)
    }

    private fun calleeName(call: KtCallExpression): String? = when (val callee = call.calleeExpression) {
        is KtNameReferenceExpression -> callee.getReferencedName()
        is KtStringTemplateExpression -> literalValue(callee)
        else -> null
    }

    private fun argumentOf(name: String?, expression: KtExpression) =
        ScriptArgument(name, literalValue(expression), expression.text)

    private fun rangeOf(statement: KtExpression) =
        lines.range(statement.textRange.startOffset, statement.textRange.endOffset)
}
