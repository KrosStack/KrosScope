package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.Visibility
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

private val flagsByModifier: Map<KtModifierKeywordToken, SymbolFlag> = mapOf(
    KtTokens.EXPECT_KEYWORD to SymbolFlag.EXPECT,
    KtTokens.ACTUAL_KEYWORD to SymbolFlag.ACTUAL,
    KtTokens.EXTERNAL_KEYWORD to SymbolFlag.EXTERNAL,
    KtTokens.ABSTRACT_KEYWORD to SymbolFlag.ABSTRACT,
    KtTokens.OPEN_KEYWORD to SymbolFlag.OPEN,
    KtTokens.OVERRIDE_KEYWORD to SymbolFlag.OVERRIDE,
    KtTokens.SUSPEND_KEYWORD to SymbolFlag.SUSPEND,
)

internal fun kotlinVisibility(declaration: KtDeclaration): Visibility = when {
    declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) -> Visibility.PRIVATE
    declaration.hasModifier(KtTokens.PROTECTED_KEYWORD) -> Visibility.PROTECTED
    declaration.hasModifier(KtTokens.INTERNAL_KEYWORD) -> Visibility.INTERNAL
    else -> Visibility.PUBLIC
}

/**
 * Flags that the modifiers imply. Members of an `expect` class are `expect` themselves, and an interface member
 * without a body is abstract.
 */
internal fun kotlinModifierFlags(declaration: KtDeclaration): Set<SymbolFlag> {
    val container = declaration.getParentOfType<KtClassOrObject>(true)
    val written = flagsByModifier.filterKeys(declaration::hasModifier).values.toMutableSet()
    if (isInsideExpectClass(container)) written += SymbolFlag.EXPECT
    if (isAbstractInterfaceMember(declaration, container)) written += SymbolFlag.ABSTRACT
    return written
}

private fun isInsideExpectClass(container: KtClassOrObject?): Boolean =
    generateSequence(container) { it.getParentOfType<KtClassOrObject>(true) }
        .any { it.hasModifier(KtTokens.EXPECT_KEYWORD) }

private fun isAbstractInterfaceMember(declaration: KtDeclaration, container: KtClassOrObject?): Boolean =
    container is KtClass && container.isInterface() && lacksImplementation(declaration)

private fun lacksImplementation(declaration: KtDeclaration): Boolean = when (declaration) {
    is KtNamedFunction -> !declaration.hasBody()
    is KtProperty -> !declaration.hasInitializer() && !declaration.hasDelegate() && !hasAccessorBody(declaration)
    else -> false
}

private fun hasAccessorBody(property: KtProperty): Boolean = property.accessors.any { it.hasBody() }
