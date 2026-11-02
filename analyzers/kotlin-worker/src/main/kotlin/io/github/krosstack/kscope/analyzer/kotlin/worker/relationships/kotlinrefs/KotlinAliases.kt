package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.kotlinrefs

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol

private const val COPY = "copy"

/**
 * Names that the compiler reads as one declaration and the source means as another: the class written in front of a
 * member of its companion object, and the parameters that stand for the properties of a data class.
 */
internal class KotlinAliases(private val session: KaSession) {
    /**
     * The class that a name stands for when it resolves to that class's companion object without saying so: in
     * `Level.fromCode(1)` the name `Level` is the class, although the compiler reads it as the companion.
     */
    fun classOfImplicitCompanion(writtenName: String, symbol: KaSymbol): KaSymbol = with(session) {
        val companion = (symbol as? KaNamedClassSymbol)?.takeIf {
            it.classKind == KaClassKind.COMPANION_OBJECT && it.name.asString() != writtenName
        }
        companion?.classId?.outerClassId?.let { findClass(it) } ?: symbol
    }

    /**
     * The property a named argument stands for: the property that a primary constructor parameter declares, and for
     * the parameter of the `copy` function of a data class the property of the same name.
     */
    fun propertyOf(parameter: KaValueParameterSymbol): KaSymbol? =
        parameter.generatedPrimaryConstructorProperty ?: copiedPropertyOf(parameter)

    private fun copiedPropertyOf(parameter: KaValueParameterSymbol): KaSymbol? = with(session) {
        val copy = parameter.containingDeclaration as? KaNamedFunctionSymbol
        val isCopy = copy?.name?.asString() == COPY && copy.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED
        val owner = copy?.containingDeclaration as? KaNamedClassSymbol
        val primary = owner?.takeIf { isCopy }?.declaredMemberScope?.constructors?.firstOrNull { it.isPrimary }
        primary?.valueParameters?.firstOrNull { it.name == parameter.name }?.generatedPrimaryConstructorProperty
    }
}
