package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.jetbrains.kotlin.psi.KtNamedFunction

private const val MAX_DEPTH = 2
private const val THIS = "this"

/** Where a body is read: how deep below the plugin's `apply`, through which functions, on which scope subjects. */
internal class ReadScope(val depth: Int, val stack: List<KtNamedFunction>, val subjects: Set<String>)

/**
 * Replaces a call of a function of the same source set by the body of that function, as `apply` of a plugin would
 * run it. Everything inlined is placed at the range of the call, since the ranges of another file mean nothing in
 * this one. A function that is not inlined is remembered in [notInlined].
 */
internal class FunctionInliner(private val index: FunctionIndex) {
    private val skipped = sortedSetOf<String>()

    val notInlined: List<String> get() = skipped.toList()

    fun inline(call: ScriptCall, scope: ReadScope): ScriptCall {
        val candidates = if (call.receiver == null) index.candidates(call) else emptyList()
        val function = candidates.firstOrNull() ?: return call
        val refused = candidates.size > 1 || scope.depth >= MAX_DEPTH || function in scope.stack
        if (refused || call.body.isNotEmpty()) skipped += call.name
        return if (refused) call else call.copy(body = inlinedBody(call, function, scope), inlined = true)
    }

    private fun inlinedBody(call: ScriptCall, function: KtNamedFunction, scope: ReadScope): List<ScriptCall> {
        val inner = ReadScope(scope.depth + 1, scope.stack + function, setOf(THIS))
        val file = function.containingKtFile
        val reader = OutlineReader(index.lines(file), SourceForms(index.lines(file), this, inner))
        val statements = function.bodyBlockExpression?.statements ?: listOfNotNull(function.bodyExpression)
        val body = bindings(call, function) + reader.readStatements(statements, nested = false).calls
        val types = function.typeParameters.mapNotNull { it.name }.zip(call.typeArguments).toMap()
        return body.map { it.withTypes(types).placedAt(call.range) }
    }

    private fun bindings(call: ScriptCall, function: KtNamedFunction): List<ScriptCall> {
        val receiver = function.receiverTypeReference?.let {
            valueBinding(THIS, it.text, call.receiver?.let { text -> ScriptArgument(null, null, text) }, call.range)
        }
        val arguments = argumentsByParameter(call, function)
        val parameters = function.namedParameters().map { parameter ->
            valueBinding(parameter.name.orEmpty(), parameter.typeReference?.text, arguments[parameter.name], call.range)
        }
        return listOfNotNull(receiver) + parameters
    }

    private fun argumentsByParameter(call: ScriptCall, function: KtNamedFunction): Map<String?, ScriptArgument> {
        val parameters = function.namedParameters().map { it.name }
        val positional = call.arguments.filter { it.name == null }
        return positional.withIndex().associate { (position, argument) -> parameters.getOrNull(position) to argument } +
            call.arguments.filter { it.name != null }.associateBy { it.name }
    }
}
