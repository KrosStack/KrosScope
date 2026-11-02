package io.github.krosstack.kscope.analyzer.kotlin.worker.outline

import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression

/** The branches of a conditional, a `try` or a loop; null for any other expression. */
internal fun controlBranches(statement: KtExpression): List<KtExpression>? = when (statement) {
    is KtIfExpression -> listOfNotNull(statement.then, statement.`else`)

    is KtWhenExpression -> statement.entries.mapNotNull { it.expression }

    is KtTryExpression -> listOfNotNull(statement.tryBlock, statement.finallyBlock?.finalExpression) +
        statement.catchClauses.mapNotNull { it.catchBody }

    is KtLoopExpression -> listOfNotNull(statement.body)

    else -> null
}

/** The keyword that opens a conditional, a `try` or a loop. */
internal fun controlKeyword(statement: KtExpression): String = when (statement) {
    is KtIfExpression -> "if"
    is KtWhenExpression -> "when"
    is KtTryExpression -> "try"
    is KtForExpression -> "for"
    is KtWhileExpression -> "while"
    is KtDoWhileExpression -> "do"
    else -> statement.text
}
