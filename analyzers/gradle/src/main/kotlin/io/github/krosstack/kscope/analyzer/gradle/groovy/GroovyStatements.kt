package io.github.krosstack.kscope.analyzer.gradle.groovy

import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.LoopingStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement

/** The calls of a list of statements and the plain assignments among them. */
internal class OutlineBody(val calls: List<ScriptCall>, val assignments: Map<String, String>)

private val assignmentOperators = setOf("=", "+=", "-=", "*=", "/=", "%=")
private val pluginModifiers = setOf("version", "apply")
private val ignoredNames = setOf("this", "super", "it")
private val unknownRange = TextRange(1, 1, 1, 1)
private val typeName = Regex("""(?:[a-z]\w*\.)*[A-Z]\w*(?:\.class)?""")

/** The expressions that the statements of a script run, with the branches of conditionals and loops listed flat. */
internal fun expressionsOf(statement: Statement): List<Expression> = when (statement) {
    is ExpressionStatement -> listOf(statement.expression)
    is BlockStatement -> statement.statements.flatMap(::expressionsOf)
    is IfStatement -> listOf(statement.ifBlock, statement.elseBlock).flatMap(::expressionsOf)
    is LoopingStatement -> expressionsOf(statement.loopBlock)
    is TryCatchStatement -> tryBranches(statement).flatMap(::expressionsOf)
    is SwitchStatement -> switchBranches(statement).flatMap(::expressionsOf)
    is ReturnStatement -> listOf(statement.expression)
    else -> emptyList()
}

private fun tryBranches(statement: TryCatchStatement): List<Statement> =
    listOf(statement.tryStatement, statement.finallyStatement) + statement.catchStatements.map { it.code }

private fun switchBranches(statement: SwitchStatement): List<Statement> =
    statement.caseStatements.map { it.code } + statement.defaultStatement

/**
 * Turns the statements of a script or of a closure into an [OutlineBody], with the encoding of the Kotlin outliner:
 * a plain assignment inside a closure is an assignment of the enclosing call, every other assignment statement is a
 * call named by its operator, plugin modifiers (`id 'x' version '1' apply false`) are assignments of the call, and
 * calls in conditionals and loops are listed as if unconditional. A call named by a string template that creates an
 * element of a given type, `"$name"(MavenPublication) { }`, is a call named by the template as written.
 */
internal class GroovyStatements(private val source: SourceText) {
    private val arguments = GroovyArguments(source)

    fun read(statements: List<Statement>, nested: Boolean): OutlineBody {
        val calls = mutableListOf<ScriptCall>()
        val assignments = linkedMapOf<String, String>()
        statements.flatMap(::expressionsOf).forEach { expression ->
            val assignment = (expression as? BinaryExpression)?.takeIf(::isAssignment)
            if (assignment != null && nested && assignment.operation.text == "=") {
                assignments[arguments.textOf(assignment.leftExpression)] = arguments.textOf(assignment.rightExpression)
            } else {
                calls += callsOf(expression, assignment)
            }
        }
        return OutlineBody(calls, assignments)
    }

    private fun callsOf(expression: Expression, assignment: BinaryExpression?): List<ScriptCall> = when {
        assignment != null -> listOf(assignmentCall(assignment))
        expression is MethodCallExpression -> listOfNotNull(callOf(expression))
        expression is VariableExpression -> listOfNotNull(bareCall(expression))
        else -> emptyList()
    }

    private fun isAssignment(expression: BinaryExpression) =
        expression !is DeclarationExpression && expression.operation.text in assignmentOperators

    private fun assignmentCall(assignment: BinaryExpression): ScriptCall {
        val value = assignment.rightExpression
        return ScriptCall(
            name = assignment.operation.text,
            receiver = arguments.textOf(assignment.leftExpression),
            arguments = listOf(ScriptArgument(null, literalValue(value), arguments.textOf(value))),
            body = emptyList(),
            assignments = emptyMap(),
            range = rangeOf(assignment),
        )
    }

    private fun bareCall(variable: VariableExpression): ScriptCall? {
        val range = rangeOf(variable)
        return ScriptCall(variable.name, null, emptyList(), emptyList(), emptyMap(), range)
            .takeIf { variable.name !in ignoredNames }
    }

    private fun callOf(call: MethodCallExpression): ScriptCall? {
        val chain = generateSequence(call) { current ->
            (current.objectExpression as? MethodCallExpression)?.takeIf { current.methodAsString in pluginModifiers }
        }.toList()
        val base = plainCall(chain.last(), rangeOf(call)) ?: return null
        val modifiers = chain.dropLast(1).asReversed().mapNotNull(::modifierOf)
        return base.copy(assignments = base.assignments + modifiers)
    }

    private fun modifierOf(modifier: MethodCallExpression): Pair<String, String>? {
        val value = (modifier.arguments as? TupleExpression)?.let(arguments::valuesOf)?.firstOrNull()
        return modifier.methodAsString?.let { name -> value?.let { name to it.expression } }
    }

    private fun plainCall(call: MethodCallExpression, range: TextRange): ScriptCall? {
        val tuple = call.arguments as? TupleExpression
        val name = call.methodAsString ?: templateName(call, tuple) ?: return null
        val bodies = tuple?.let(arguments::closuresOf).orEmpty().map { read(listOf(it.code), nested = true) }
        return ScriptCall(
            name = name,
            receiver = receiverOf(call),
            arguments = tuple?.let(arguments::valuesOf).orEmpty(),
            body = bodies.flatMap { it.calls },
            assignments = bodies.fold(emptyMap()) { all, body -> all + body.assignments },
            range = range,
        )
    }

    private fun templateName(call: MethodCallExpression, tuple: TupleExpression?): String? {
        val isTyped = tuple?.let(arguments::valuesOf).orEmpty().any { typeName.matches(it.expression.trim()) }
        return arguments.textOf(call.method).takeIf { isTyped }
    }

    private fun receiverOf(call: MethodCallExpression): String? {
        val target = call.objectExpression
        val isImplicit = call.isImplicitThis || (target is VariableExpression && target.isThisExpression)
        return if (isImplicit) null else arguments.textOf(target)
    }

    private fun rangeOf(node: Expression): TextRange = source.range(node) ?: unknownRange
}
