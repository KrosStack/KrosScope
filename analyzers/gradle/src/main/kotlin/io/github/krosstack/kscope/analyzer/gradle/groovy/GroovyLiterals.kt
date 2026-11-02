package io.github.krosstack.kscope.analyzer.gradle.groovy

import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression

/** The value of a string, number or boolean literal without interpolation, as text; null for anything else. */
internal fun literalValue(expression: Expression): String? = when (expression) {
    is ConstantExpression -> constantText(expression)
    is UnaryMinusExpression -> negatedNumber(expression)
    else -> null
}

private fun constantText(constant: ConstantExpression): String? = when (val value = constant.value) {
    is String -> value
    is Number, is Boolean -> value.toString()
    else -> null
}

private fun negatedNumber(expression: UnaryMinusExpression): String? {
    val operand = expression.expression as? ConstantExpression ?: return null
    return (operand.value as? Number)?.let { "-$it" }
}
