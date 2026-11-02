package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import io.github.krosstack.kscope.analyzer.kotlin.worker.declarations.LineIndex
import io.github.krosstack.kscope.analyzer.kotlin.worker.syntax.literalValue
import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

/** Reads assignment statements whose target is a name or a qualified name. */
internal class AssignmentReader(private val lines: LineIndex) {
    fun entryOf(assignment: KtBinaryExpression): Pair<String, String>? =
        targetAndValue(assignment)?.let { (target, value) -> target.text to value.text }

    fun callOf(assignment: KtBinaryExpression): ScriptCall? = targetAndValue(assignment)?.let { (target, value) ->
        ScriptCall(
            name = assignment.operationReference.text,
            receiver = target.text,
            arguments = listOf(ScriptArgument(null, literalValue(value), value.text)),
            body = emptyList(),
            assignments = emptyMap(),
            range = lines.range(assignment.textRange.startOffset, assignment.textRange.endOffset),
        )
    }

    private fun targetAndValue(assignment: KtBinaryExpression): Pair<KtExpression, KtExpression>? {
        val target = assignment.left
        val value = assignment.right
        return if (target != null && value != null && isNameTarget(target)) target to value else null
    }

    private fun isNameTarget(target: KtExpression): Boolean = target is KtNameReferenceExpression ||
        (target is KtQualifiedExpression && target.selectorExpression is KtNameReferenceExpression)
}
