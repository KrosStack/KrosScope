package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Target
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.WorkspaceIds
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtParameter

private const val CONSTRUCTOR = "constructor"
private const val GENERATED = "generated"
private const val SAM_CONSTRUCTOR = "sam-constructor"

/**
 * Names the workspace declaration that a symbol of the Analysis API stands for, from the syntax of its declaration.
 * Symbols that the compiler makes up have a declaration of something else: an implicit constructor is declared by its
 * class, `copy` by the primary constructor, `values` and `entries` by the enum, `componentN` by the parameter it
 * reads. They are mapped to the nearest declared symbol and say so in the sub-kind.
 */
internal class KotlinWorkspaceTargets(private val ids: WorkspaceIds) {
    fun of(symbol: KaDeclarationSymbol, access: Access, moduleHint: String?): Target? {
        val declaration = symbol.psi
        return when {
            symbol is KaSamConstructorSymbol -> samConstructor(declaration, moduleHint)
            declaration is KtDeclaration -> ofKotlin(symbol, declaration, access, moduleHint)
            declaration is PsiMember -> ofJava(symbol, declaration, access, moduleHint)
            else -> null
        }
    }

    private fun samConstructor(declaration: Any?, hint: String?): Target? = when (declaration) {
        is KtClassOrObject -> ids.of(declaration, hint)?.let { Target.Workspace(it, SAM_CONSTRUCTOR, true) }
        is PsiClass -> ids.of(declaration, hint)?.let { Target.Workspace(it, SAM_CONSTRUCTOR, true) }
        else -> null
    }

    private fun ofKotlin(
        symbol: KaDeclarationSymbol,
        declaration: KtDeclaration,
        access: Access,
        hint: String?,
    ): Target? {
        val isConstructor = symbol is KaConstructorSymbol
        val isGenerated = symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED
        return when {
            isConstructor && declaration is KtClassOrObject -> kotlinClass(declaration, CONSTRUCTOR, hint)
            isGenerated && !isConstructor -> generatedMember(declaration, hint)
            else -> declared(declaration, variableSubKind(symbol, access), hint)
        }
    }

    private fun generatedMember(declaration: KtDeclaration, hint: String?): Target? = when (declaration) {
        is KtParameter -> declared(declaration, null, hint)
        is KtConstructor<*> -> kotlinClass(declaration.getContainingClassOrObject(), GENERATED, hint)
        is KtClassOrObject -> kotlinClass(declaration, GENERATED, hint)
        else -> null
    }

    private fun declared(declaration: KtDeclaration, subKind: String?, hint: String?): Target? {
        val isInterface = declaration is KtClass && declaration.isInterface()
        return ids.of(declaration, hint)?.let { Target.Workspace(it, subKind, isInterface) }
    }

    private fun kotlinClass(declaration: KtClassOrObject, subKind: String, hint: String?): Target? =
        declared(declaration, subKind, hint)

    private fun ofJava(symbol: KaDeclarationSymbol, member: PsiMember, access: Access, hint: String?): Target? {
        val owner = if (member is PsiClass) member else member.containingClass
        return when {
            symbol is KaConstructorSymbol && member is PsiClass -> javaClass(member, CONSTRUCTOR, hint)
            !member.isPhysical && owner != null -> javaClass(owner, generatedKind(symbol), hint)
            else -> javaDeclared(member, variableSubKind(symbol, access), hint)
        }
    }

    private fun javaDeclared(member: PsiMember, subKind: String?, hint: String?): Target? {
        val isInterface = (member as? PsiClass)?.isInterface == true
        return ids.of(member, hint)?.let { Target.Workspace(it, subKind, isInterface) }
    }

    private fun generatedKind(symbol: KaDeclarationSymbol): String =
        if (symbol is KaConstructorSymbol) CONSTRUCTOR else GENERATED

    private fun javaClass(declaration: PsiClass, subKind: String, hint: String?): Target? =
        ids.of(declaration, hint)?.let { Target.Workspace(it, subKind, declaration.isInterface) }

    private fun variableSubKind(symbol: KaDeclarationSymbol, access: Access): String? =
        access.subKind.takeIf { symbol is KaPropertySymbol || symbol is KaJavaFieldSymbol }
}
