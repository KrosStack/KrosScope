package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.LineIndex
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

/**
 * The parameters of a function that have a name. The syntax tree of a parameter list with a trailing comma holds
 * parameters without a name, which are no parameters.
 */
internal fun KtNamedFunction.namedParameters(): List<KtParameter> = valueParameters.filter { it.name != null }

/** The functions of the Kotlin sources of one source set, by simple name, and the line index of each source. */
internal class FunctionIndex(files: List<KtFile>) {
    private val byName: Map<String, List<KtNamedFunction>> = files
        .flatMap { functionsOf(it.declarations) }
        .filterNot { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
        .groupBy { it.name.orEmpty() }

    private val lineIndexes: Map<KtFile, LineIndex> = files.associateWith { LineIndex(it.text) }

    fun lines(file: KtFile): LineIndex = lineIndexes.getValue(file)

    /** The functions a call by simple name can mean: the name and the arguments fit. */
    fun candidates(call: ScriptCall): List<KtNamedFunction> = byName[call.name].orEmpty().filter { accepts(it, call) }

    private fun functionsOf(declarations: List<KtDeclaration>): List<KtNamedFunction> = declarations.flatMap {
        when (it) {
            is KtNamedFunction -> listOf(it)
            is KtClassOrObject -> functionsOf(it.declarations)
            else -> emptyList()
        }
    }

    private fun accepts(function: KtNamedFunction, call: ScriptCall): Boolean {
        val parameters = function.namedParameters()
        val lambdas = if (call.body.isEmpty() && call.assignments.isEmpty()) 0 else 1
        val supplied = call.arguments.size + lambdas
        val required = parameters.count { !it.hasDefaultValue() && !it.isVarArg }
        val fits = supplied >= required && (supplied <= parameters.size || parameters.any { it.isVarArg })
        val namesExist = call.arguments.mapNotNull { it.name }.all { name -> parameters.any { it.name == name } }
        return fits && namesExist && call.typeArguments.size <= function.typeParameters.size
    }
}
