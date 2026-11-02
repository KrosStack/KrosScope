package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.LineIndex
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScriptInitializer

private val assignmentTokens =
    setOf(KtTokens.EQ, KtTokens.PLUSEQ, KtTokens.MINUSEQ, KtTokens.MULTEQ, KtTokens.DIVEQ, KtTokens.PERCEQ)

/** The calls of a list of statements and the plain assignments among them. */
internal class Body(val calls: List<ScriptCall>, val assignments: Map<String, String>)

/**
 * Turns the statements of a script or of a lambda into a [Body]. A reader given [forms] reads the body of a function:
 * it keeps conditionals and loops as calls instead of listing their branches as if unconditional, keeps locals as
 * `val` calls, and inlines the functions that the body calls.
 */
internal class OutlineReader(lines: LineIndex, private val forms: SourceForms? = null) {
    private val calls = CallReader(lines) { readStatements(it, nested = true) }
    private val assignments = AssignmentReader(lines)

    fun readStatements(statements: List<KtExpression>, nested: Boolean): Body {
        val readCalls = mutableListOf<ScriptCall>()
        val readAssignments = linkedMapOf<String, String>()
        statements.flatMap(::leavesOf).forEach { leaf ->
            val assignment = (leaf as? KtBinaryExpression)?.takeIf { it.operationToken in assignmentTokens }
            when {
                assignment != null && nested && assignment.operationToken == KtTokens.EQ ->
                    assignments.entryOf(assignment)?.let { readAssignments += it }

                assignment != null -> readCalls += listOfNotNull(assignments.callOf(assignment))

                leaf is KtProperty -> readCalls += listOfNotNull(propertyCall(leaf))

                else -> readCalls += listOfNotNull(callOf(leaf))
            }
        }
        return Body(readCalls, readAssignments)
    }

    private fun propertyCall(property: KtProperty): ScriptCall? =
        if (forms == null) calls.delegatedCall(property) else forms.valueCall(property) { calls.callOf(it, property) }

    private fun callOf(leaf: KtExpression): ScriptCall? {
        if (forms == null) return calls.callOf(leaf, leaf)
        return forms.controlCall(leaf) { readStatements(it, nested = true) }
            ?: calls.callOf(leaf, leaf)?.let(forms::inlined)
    }

    private fun leavesOf(statement: KtExpression): List<KtExpression> =
        forms?.unwrap(statement)?.flatMap(::leavesOf) ?: when {
            statement is KtLabeledExpression -> listOfNotNull(statement.baseExpression).flatMap(::leavesOf)
            statement is KtScriptInitializer -> listOfNotNull(statement.body).flatMap(::leavesOf)
            forms == null -> controlBranches(statement)?.flatMap(::branchLeaves) ?: listOf(statement)
            else -> listOf(statement)
        }

    private fun branchLeaves(branch: KtExpression): List<KtExpression> =
        if (branch is KtBlockExpression) branch.statements.flatMap(::leavesOf) else leavesOf(branch)
}
