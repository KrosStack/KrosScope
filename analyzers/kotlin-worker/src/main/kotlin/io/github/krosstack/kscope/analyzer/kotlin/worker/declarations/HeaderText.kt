package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtParameter

private const val MAX_SIGNATURE_LENGTH = 400

private val whitespace = Regex("""\s+""")
private val afterOpeningParenthesis = Regex("""\(\s+""")
private val beforeClosingParenthesis = Regex("""\s+\)""")
private val trailingComma = Regex(""",\s*\)""")
private val trailingSeparator = Regex("""\s*[;=,]$""")

/**
 * Renders the declaration header as written: the text of [element] before [endOffset] on one line, without comments
 * and without the annotations of the declaration itself (annotations of its parameters stay).
 */
internal fun headerText(element: PsiElement, endOffset: Int): String {
    val text = StringBuilder()
    appendHeader(element, endOffset, text)
    return normalizeHeader(text.toString())
}

internal fun normalizeHeader(text: String): String {
    val oneLine = text.replace(whitespace, " ").trim()
        .replace(afterOpeningParenthesis, "(").replace(beforeClosingParenthesis, ")")
        .replace(trailingComma, ")").replace(trailingSeparator, "")
    return if (oneLine.length > MAX_SIGNATURE_LENGTH) oneLine.take(MAX_SIGNATURE_LENGTH) + "..." else oneLine
}

private fun appendHeader(element: PsiElement, endOffset: Int, out: StringBuilder) {
    if (element.textRange.startOffset >= endOffset || isOmitted(element)) return
    val first = element.firstChild
    if (first == null) {
        out.append(element.text)
        return
    }
    generateSequence(first) { it.nextSibling }.forEach { appendHeader(it, endOffset, out) }
}

private fun isOmitted(element: PsiElement): Boolean =
    element is PsiComment || (isAnnotation(element) && isOnDeclaration(element))

private fun isAnnotation(element: PsiElement): Boolean =
    element is KtAnnotationEntry || element is KtAnnotation || element is PsiAnnotation

private fun isOnDeclaration(annotation: PsiElement): Boolean {
    val owner = annotation.parent?.takeIf { it is KtModifierList || it is PsiModifierList }?.parent ?: return false
    return (owner is KtDeclaration && owner !is KtParameter) ||
        (owner is PsiModifierListOwner && owner !is PsiParameter)
}
