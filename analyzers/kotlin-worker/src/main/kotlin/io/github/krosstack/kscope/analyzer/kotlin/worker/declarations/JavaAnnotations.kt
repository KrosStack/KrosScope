package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPrefixExpression

private const val DEFAULT_PARAMETER = "value"

/** Reads the annotations of Java declarations: names, qualified as far as the file and the classpath allow. */
internal class JavaAnnotations(file: PsiJavaFile) {
    val imports: ImportScope = importScope(file)

    fun usesOf(owner: PsiModifierListOwner): List<AnnotationUse> = owner.annotations.mapNotNull(::useOf)

    private fun useOf(annotation: PsiAnnotation): AnnotationUse? {
        val reference = annotation.nameReferenceElement ?: return null
        val written = reference.qualifiedName ?: reference.text
        val qualified = when {
            '.' in written -> written
            else -> resolvedName(annotation) ?: imports.explicit[written]
        }
        return AnnotationUse(written, qualified, argumentsOf(annotation))
    }

    private fun resolvedName(annotation: PsiAnnotation): String? =
        runCatching { (annotation.nameReferenceElement?.resolve() as? PsiClass)?.qualifiedName }.getOrNull()

    private fun argumentsOf(annotation: PsiAnnotation): Map<String, String> {
        val pairs = annotation.parameterList.attributes
        return pairs.mapNotNull { pair ->
            val name = pair.name ?: DEFAULT_PARAMETER
            literalOf(pair.value)?.let { name to it }
        }.toMap()
    }
}

private fun importScope(file: PsiJavaFile): ImportScope {
    val statements = file.importList?.importStatements.orEmpty().filter { it.qualifiedName != null }
    val explicit = statements.filterNot { it.isOnDemand }.associate { statement ->
        val qualified = checkNotNull(statement.qualifiedName)
        qualified.substringAfterLast('.') to qualified
    }
    val onDemand = statements.filter { it.isOnDemand }.map { checkNotNull(it.qualifiedName) }
    return ImportScope(explicit, onDemand)
}

private fun literalOf(value: PsiAnnotationMemberValue?): String? = when (value) {
    is PsiLiteralExpression -> value.value.takeIf { it is String || it is Number || it is Boolean }?.toString()
    is PsiPrefixExpression -> signedNumber(value)
    else -> null
}

private fun signedNumber(expression: PsiPrefixExpression): String? {
    val operand = (expression.operand as? PsiLiteralExpression)?.value as? Number
    val isSign = expression.operationTokenType in setOf(JavaTokenType.MINUS, JavaTokenType.PLUS)
    return if (operand != null && isSign) expression.operationSign.text + operand else null
}
