package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import com.intellij.psi.PsiElement
import io.github.krosstack.kscope.model.SymbolDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty

internal const val CONSTRUCTOR_NAME = "<init>"
private const val COMPANION_NAME = "Companion"
private const val FACADE_SUFFIX = "Kt"

/**
 * Identity of a Kotlin declaration computed from its syntax alone, so that a producer that only parses and one that
 * resolves agree on it. Top-level callables hang directly off the package, except the private ones, which belong to
 * their file and hang off its facade class (`Name.kt` is `NameKt`): two files of one package may each declare a private
 * `helper`. Returns null for declarations that are not symbols: anything inside a body, and anonymous objects.
 */
internal object KotlinDescriptors {
    fun of(declaration: KtDeclaration): SymbolDescriptor? {
        val own = KotlinSegments.of(declaration)
        val types = enclosingTypeNames(declaration)
        if (own == null || types == null) return null
        val packageDescriptor = declaration.containingKtFile.packageFqName.pathSegments()
            .fold(SymbolDescriptor()) { descriptor, name -> descriptor.namespace(name.asString()) }
        val scopes = types + listOfNotNull(facadeNameOfPrivate(declaration))
        return scopes.fold(packageDescriptor) { descriptor, name -> descriptor.type(name) } + own
    }

    private fun facadeNameOfPrivate(declaration: KtDeclaration): String? {
        val isCallable = declaration is KtNamedFunction || declaration is KtProperty
        val isPrivate = declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)
        val isFilePrivate = isCallable && isPrivate && declaration.parent is KtFile
        return if (isFilePrivate) facadeName(declaration.containingKtFile) else null
    }

    private fun facadeName(file: KtFile): String =
        file.name.substringBeforeLast('.').replaceFirstChar { it.uppercase() } + FACADE_SUFFIX

    fun typeName(declaration: KtClassOrObject): String? =
        declaration.name ?: COMPANION_NAME.takeIf { declaration is KtObjectDeclaration && declaration.isCompanion() }

    fun qualifiedName(declaration: KtDeclaration): String? {
        val types = enclosingTypeNames(declaration)
        val name = if (declaration is KtConstructor<*>) CONSTRUCTOR_NAME else declaration.name
        if (types == null || name == null) return null
        val packageName = declaration.containingKtFile.packageFqName.asString()
        return (listOf(packageName).filter { it.isNotEmpty() } + types + name).joinToString(".")
    }

    // The body of an enum entry is like an anonymous object: nothing in it can be named from outside.
    private fun enclosingTypeName(ancestor: PsiElement): String? =
        (ancestor as? KtClassOrObject)?.takeUnless { it is KtEnumEntry }?.let(::typeName)

    private fun enclosingTypeNames(declaration: KtDeclaration): List<String>? {
        val ancestors = generateSequence(declaration.parent) { it.parent }
            .takeWhile { it !is KtFile }
            .filterNot { it is KtClassBody || it is KtParameterList || it is KtPrimaryConstructor }
            .toList()
        val names = ancestors.map(::enclosingTypeName)
        return if (null in names) null else names.filterNotNull().reversed()
    }
}
