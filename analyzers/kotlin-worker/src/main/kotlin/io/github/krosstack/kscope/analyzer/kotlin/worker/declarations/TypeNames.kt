package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import io.github.krosstack.kscope.model.DeclaredParameterType
import io.github.krosstack.kscope.model.MethodDisambiguator
import org.jetbrains.kotlin.psi.KtDynamicType
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtIntersectionType
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

private const val UNKNOWN_TYPE = "?"

/** The simple name of a declared Kotlin type: no package, type arguments, nullability mark or annotations. */
internal fun kotlinTypeName(reference: KtTypeReference?): String =
    reference?.let { kotlinTypeName(it, it.typeElement) } ?: UNKNOWN_TYPE

private fun kotlinTypeName(reference: KtTypeReference, element: KtTypeElement?): String = when (element) {
    null -> unparenthesized(reference.text)
    is KtNullableType -> kotlinTypeName(reference, element.innerType)
    is KtFunctionType -> MethodDisambiguator.FUNCTION_TYPE
    is KtUserType -> element.referencedName ?: UNKNOWN_TYPE
    is KtDynamicType -> "dynamic"
    is KtIntersectionType -> kotlinTypeName(element.getLeftTypeRef())
    else -> element.text
}

private fun unparenthesized(text: String): String = text.filterNot { it == '(' || it == ')' }.ifEmpty { UNKNOWN_TYPE }

internal fun kotlinParameterTypes(parameters: List<KtParameter>): List<DeclaredParameterType> =
    parameters.map { DeclaredParameterType(kotlinTypeName(it.typeReference), it.isVarArg) }

/** The simple name of a declared Java type, with `[]` for each array dimension and without type arguments. */
internal fun javaTypeName(type: PsiType): String = when (type) {
    is PsiEllipsisType -> javaTypeName(type.componentType)
    is PsiArrayType -> javaTypeName(type.componentType) + "[]"
    is PsiClassType -> type.className ?: UNKNOWN_TYPE
    else -> unparenthesized(type.presentableText)
}

internal fun javaParameterTypes(parameters: Array<PsiParameter>): List<DeclaredParameterType> =
    parameters.map { DeclaredParameterType(javaTypeName(it.type), it.isVarArgs) }
