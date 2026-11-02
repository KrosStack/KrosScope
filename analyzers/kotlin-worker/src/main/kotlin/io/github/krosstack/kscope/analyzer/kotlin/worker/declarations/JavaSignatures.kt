package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod

private const val TO_END = Int.MAX_VALUE

/** The declaration header as written on one line: modifiers, type, name and parameters, but no body or initializer. */
internal fun javaSignature(member: PsiMember): String = when (member) {
    is PsiClass -> headerText(member, member.lBrace?.textRange?.startOffset ?: TO_END)
    is PsiMethod -> headerText(member, member.body?.textRange?.startOffset ?: TO_END)
    is PsiEnumConstant -> headerText(member, member.initializingClass?.textRange?.startOffset ?: TO_END)
    is PsiField -> fieldSignature(member)
    else -> headerText(member, TO_END)
}

// A field is rebuilt from its parts because one declaration may introduce several fields sharing a type.
private fun fieldSignature(field: PsiField): String {
    val modifiers = field.modifierList?.let { headerText(it, TO_END) }.orEmpty()
    return normalizeHeader("$modifiers ${field.typeElement?.text.orEmpty()} ${field.name}")
}
