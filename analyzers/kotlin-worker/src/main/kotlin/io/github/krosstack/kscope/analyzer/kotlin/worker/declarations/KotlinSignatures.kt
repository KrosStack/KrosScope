package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

private const val TO_END = Int.MAX_VALUE

/** The declaration header as written on one line: modifiers, name, parameters and types, but no body or initializer. */
internal fun kotlinSignature(declaration: KtDeclaration): String = when (declaration) {
    is KtClassOrObject -> headerText(declaration, declaration.getBody()?.textRange?.startOffset ?: TO_END)
    is KtNamedFunction -> headerText(declaration, functionHeaderEnd(declaration))
    is KtProperty -> headerText(declaration, propertyHeaderEnd(declaration))
    is KtParameter -> headerText(declaration, declaration.defaultValue?.textRange?.startOffset ?: TO_END)
    is KtConstructor<*> -> constructorSignature(declaration)
    else -> headerText(declaration, TO_END)
}

private fun functionHeaderEnd(function: KtNamedFunction): Int {
    val bodyStart = function.bodyExpression?.textRange?.startOffset
    val expressionBodyStart = function.equalsToken?.textRange?.startOffset
    return (if (function.hasBlockBody()) bodyStart else expressionBodyStart) ?: TO_END
}

private fun propertyHeaderEnd(property: KtProperty): Int = listOfNotNull(
    property.initializer,
    property.delegate,
    property.getter,
    property.setter,
).minOfOrNull { it.textRange.startOffset } ?: TO_END

private fun constructorSignature(constructor: KtConstructor<*>): String {
    val parameters = constructor.valueParameterList ?: return "constructor"
    val modifiers = headerText(constructor, parameters.textRange.startOffset)
    val keyword = if (constructor.getConstructorKeyword() != null) modifiers else "$modifiers constructor"
    return normalizeHeader(keyword.trim() + headerText(parameters, TO_END))
}
