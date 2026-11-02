package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiRecordComponent
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.Visibility

/** Java has no package-private visibility in the model; it is the closest to `internal`. */
internal fun javaVisibility(member: PsiMember): Visibility = when {
    member is PsiRecordComponent || member.hasModifierProperty(PsiModifier.PRIVATE) -> Visibility.PRIVATE
    member.hasModifierProperty(PsiModifier.PROTECTED) -> Visibility.PROTECTED
    member.hasModifierProperty(PsiModifier.PUBLIC) -> Visibility.PUBLIC
    else -> Visibility.INTERNAL
}

/** Flags that the modifiers and annotations of a Java member imply; implicit modifiers are those the PSI reports. */
internal fun javaFlags(member: PsiMember, uses: List<AnnotationUse>): Set<SymbolFlag> = buildSet {
    if (isAbstractMember(member)) add(SymbolFlag.ABSTRACT)
    if (member.hasModifierProperty(PsiModifier.STATIC)) add(SymbolFlag.STATIC)
    if (member.hasModifierProperty(PsiModifier.NATIVE)) add(SymbolFlag.EXTERNAL)
    if (uses.any { it.simpleName == "Override" }) add(SymbolFlag.OVERRIDE)
    if (isOpenMember(member)) add(SymbolFlag.OPEN)
    if (isDeprecatedByDocumentation(member)) add(SymbolFlag.DEPRECATED)
}

private fun isAbstractMember(member: PsiMember): Boolean =
    member.hasModifierProperty(PsiModifier.ABSTRACT) && !(member is PsiClass && member.isInterface)

private fun isOpenMember(member: PsiMember): Boolean = when (member) {
    is PsiClass -> isExtendable(member)
    is PsiMethod -> isOverridable(member)
    else -> false
}

private fun isExtendable(declaration: PsiClass): Boolean =
    !declaration.isInterface && !declaration.isEnum && !declaration.isRecord &&
        !declaration.hasModifierProperty(PsiModifier.FINAL)

private fun isOverridable(method: PsiMethod): Boolean {
    val ownerAllowsSubclasses = method.containingClass?.let(::isExtendable) == true
    val methodAllowsOverride = listOf(PsiModifier.FINAL, PsiModifier.STATIC, PsiModifier.PRIVATE)
        .none(method::hasModifierProperty)
    return ownerAllowsSubclasses && methodAllowsOverride && !method.isConstructor
}

private fun isDeprecatedByDocumentation(member: PsiMember): Boolean =
    (member as? PsiDocCommentOwner)?.docComment?.findTagByName("deprecated") != null
