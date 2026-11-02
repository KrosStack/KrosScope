package io.github.krosstack.kscope.analyzer.kotlin.worker.digest

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

/**
 * The text of a Kotlin file that the analysis of another file can depend on: every token in order, one per line,
 * without whitespace and comments, and with `{}` in place of each body that no other file can see.
 */
internal class ApiText {
    fun of(file: KtFile): String = buildString { ApiTextWriter(ContractCalls(file), this).append(file) }
}

private class ApiTextWriter(private val contracts: ContractCalls, private val text: StringBuilder) {
    fun append(element: PsiElement) {
        when {
            element is PsiWhiteSpace || element is PsiComment -> Unit
            element is KtBlockExpression && removable(element) -> text.append("{}\n")
            element.firstChild == null -> text.append(element.text).append('\n')
            else -> generateSequence(element.firstChild) { it.nextSibling }.forEach(::append)
        }
    }

    private fun removable(body: KtBlockExpression): Boolean = when (val declaration = body.parent) {
        is KtNamedFunction -> declaration.bodyExpression === body && removableFunction(declaration, body)
        is KtPropertyAccessor -> declaration.bodyExpression === body && removableAccessor(declaration, body)
        is KtSecondaryConstructor -> declaration.bodyExpression === body
        is KtClassInitializer -> declaration.body === body
        else -> false
    }

    private fun removableFunction(function: KtNamedFunction, body: KtBlockExpression): Boolean =
        function.name != null && function.hasBlockBody() &&
            !function.hasModifier(KtTokens.INLINE_KEYWORD) && !contracts.opens(body)

    private fun removableAccessor(accessor: KtPropertyAccessor, body: KtBlockExpression): Boolean =
        !accessor.hasModifier(KtTokens.INLINE_KEYWORD) && !accessor.property.hasModifier(KtTokens.INLINE_KEYWORD) &&
            !contracts.opens(body)
}

/**
 * Recognises a body that begins with a call of `contract`. The call is matched by name, so the name counts whatever
 * it resolves to, and so does every alias under which the file imports something named `contract`.
 */
private class ContractCalls(file: KtFile) {
    private val names: Set<String> = file.importDirectives
        .filter { it.importedFqName?.shortName()?.asString() == CONTRACT }
        .mapNotNullTo(mutableSetOf(CONTRACT)) { it.aliasName }

    fun opens(body: KtBlockExpression): Boolean {
        val call = when (val statement = body.statements.firstOrNull()) {
            is KtCallExpression -> statement
            is KtQualifiedExpression -> statement.selectorExpression as? KtCallExpression
            else -> null
        }
        return call?.calleeExpression?.text?.removeSurrounding("`") in names
    }

    private companion object {
        const val CONTRACT = "contract"
    }
}
