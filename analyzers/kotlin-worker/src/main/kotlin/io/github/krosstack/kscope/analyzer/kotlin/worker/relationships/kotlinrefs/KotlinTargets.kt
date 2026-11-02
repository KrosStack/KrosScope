package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Access
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Target
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries.KotlinLibraryIds
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol

private const val MAX_OVERRIDE_DEPTH = 8

private val fakeOverrideOrigins = setOf(KaSymbolOrigin.SUBSTITUTION_OVERRIDE, KaSymbolOrigin.INTERSECTION_OVERRIDE)

/**
 * Maps the symbols that the Analysis API resolves a Kotlin reference to onto the symbol model. A workspace target gets
 * the id that its declaration syntax gives, wherever it is declared; a library target gets a library id when the
 * caller wants one; parameters, locals and other undeclared symbols map to nothing. A synthetic property of a Java
 * class is the call of its getter or setter.
 */
internal class KotlinTargets(
    private val session: KaSession,
    private val workspace: KotlinWorkspaceTargets,
    private val libraries: KotlinLibraryIds,
) {
    fun map(symbol: KaSymbol, access: Access, withLibrary: Boolean): Target? {
        val original = originalOf(symbol) as? KaDeclarationSymbol ?: return null
        return when (original) {
            is KaBackingFieldSymbol -> null
            is KaValueParameterSymbol -> propertyOf(original, access, withLibrary)
            is KaSyntheticJavaPropertySymbol -> accessorCall(original, access, withLibrary)
            else -> declaredTarget(original, access, withLibrary)
        }
    }

    // A constructor parameter that declares a property is that property, whichever scope the compiler read it in.
    private fun propertyOf(parameter: KaValueParameterSymbol, access: Access, withLibrary: Boolean): Target? =
        parameter.generatedPrimaryConstructorProperty?.let { declaredTarget(it, access, withLibrary) }

    private fun declaredTarget(symbol: KaDeclarationSymbol, access: Access, withLibrary: Boolean): Target? =
        workspace.of(symbol, access, moduleNameOf(symbol))
            ?: implicitConstructor(symbol)
            ?: libraryTarget(symbol, withLibrary)

    private fun originalOf(symbol: KaSymbol): KaSymbol = with(session) {
        generateSequence(symbol) { current ->
            (current as? KaCallableSymbol)?.takeIf { it.origin in fakeOverrideOrigins }?.fakeOverrideOriginal
        }.take(MAX_OVERRIDE_DEPTH).last()
    }

    private fun accessorCall(property: KaSyntheticJavaPropertySymbol, access: Access, withLibrary: Boolean): Target? {
        val getter = property.javaGetterSymbol
        val accessor = if (access == Access.WRITE) property.javaSetterSymbol ?: getter else getter
        val target = map(accessor, Access.NONE, withLibrary)
        return if (target is Target.Workspace) target.copy(subKind = access.subKind, isAccessorCall = true) else target
    }

    // A constructor that the front end made up has no declaration of its own, so it is the constructor of its class.
    private fun implicitConstructor(symbol: KaDeclarationSymbol): Target? {
        val classId = (symbol as? KaConstructorSymbol)?.containingClassId
        val owner = classId?.let { with(session) { findClass(it) } }
        val target = owner?.let { workspace.of(it, Access.NONE, moduleNameOf(it)) }
        return (target as? Target.Workspace)?.copy(subKind = "constructor")
    }

    private fun libraryTarget(symbol: KaDeclarationSymbol, withLibrary: Boolean): Target? {
        if (!withLibrary || isInWorkspace(symbol)) return null
        val isInterface = (symbol as? KaClassSymbol)?.classKind == KaClassKind.INTERFACE
        return libraries.idOf(symbol)?.let { Target.Library(it, isInterface) }
    }

    private fun isInWorkspace(symbol: KaDeclarationSymbol): Boolean = with(session) {
        symbol.containingModule is KaSourceModule
    }

    private fun moduleNameOf(symbol: KaDeclarationSymbol): String? = with(session) {
        (symbol.containingModule as? KaSourceModule)?.name
    }
}
