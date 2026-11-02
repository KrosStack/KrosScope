package io.github.krosstack.kscope.analyzer.gradle.groovy

import io.github.krosstack.kscope.spi.worker.ScriptArgument
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TupleExpression

/** Reads the arguments of a call: positional values, `name: value` entries, and the closures that become its body. */
internal class GroovyArguments(private val source: SourceText) {
    fun closuresOf(arguments: TupleExpression): List<ClosureExpression> =
        arguments.expressions.filterIsInstance<ClosureExpression>()

    fun valuesOf(arguments: TupleExpression): List<ScriptArgument> =
        arguments.expressions.filter { it !is ClosureExpression }.flatMap(::argumentsOf)

    /**
     * The source text of an expression. The parser places a property or method access at its dot, and a call at its
     * opening parenthesis, so the text starts at the leftmost name of the chain.
     */
    fun textOf(expression: Expression): String = source.slice(leftmostOf(expression), expression) ?: expression.text

    private fun leftmostOf(expression: Expression): Expression = when (expression) {
        is PropertyExpression -> leftmostOf(expression.objectExpression)
        is MethodCallExpression -> callStartOf(expression)
        else -> expression
    }

    private fun callStartOf(call: MethodCallExpression): Expression =
        if (call.isImplicitThis) call.method else leftmostOf(call.objectExpression)

    private fun argumentsOf(expression: Expression): List<ScriptArgument> = when (expression) {
        is NamedArgumentListExpression -> entriesOf(expression)
        is MapExpression -> entriesOf(expression)
        else -> listOf(ScriptArgument(null, literalValue(expression), textOf(expression)))
    }

    private fun entriesOf(map: MapExpression): List<ScriptArgument> = map.mapEntryExpressions.map(::entryOf)

    private fun entryOf(entry: MapEntryExpression): ScriptArgument {
        val name = literalValue(entry.keyExpression) ?: textOf(entry.keyExpression)
        return ScriptArgument(name, literalValue(entry.valueExpression), textOf(entry.valueExpression))
    }
}
