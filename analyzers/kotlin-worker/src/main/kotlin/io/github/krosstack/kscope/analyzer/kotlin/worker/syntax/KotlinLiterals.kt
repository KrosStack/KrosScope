package io.github.krosstack.kscope.analyzer.kotlin.worker.syntax

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** The value of a string, number or boolean literal without interpolation, as text; null for anything else. */
internal fun literalValue(expression: KtExpression?): String? = when (expression) {
    is KtParenthesizedExpression -> literalValue(expression.expression)
    is KtStringTemplateExpression -> stringValue(expression)
    is KtConstantExpression -> constantValue(expression)
    is KtPrefixExpression -> signedNumber(expression)
    else -> null
}

private fun stringValue(expression: KtStringTemplateExpression): String? {
    val entries = expression.entries
    if (entries.any { it !is KtLiteralStringTemplateEntry && it !is KtEscapeStringTemplateEntry }) return null
    return entries.joinToString("") { entry ->
        if (entry is KtEscapeStringTemplateEntry) entry.unescapedValue else entry.text
    }
}

private fun constantValue(expression: KtConstantExpression): String? = when (expression.node.elementType) {
    KtNodeTypes.INTEGER_CONSTANT, KtNodeTypes.FLOAT_CONSTANT, KtNodeTypes.BOOLEAN_CONSTANT -> expression.text
    else -> null
}

private fun signedNumber(expression: KtPrefixExpression): String? {
    val operator = expression.operationToken
    val operand = expression.baseExpression as? KtConstantExpression ?: return null
    val isSign = operator == KtTokens.MINUS || operator == KtTokens.PLUS
    val isNumber = operand.node.elementType == KtNodeTypes.INTEGER_CONSTANT ||
        operand.node.elementType == KtNodeTypes.FLOAT_CONSTANT
    return if (isSign && isNumber) expression.operationReference.text + operand.text else null
}
