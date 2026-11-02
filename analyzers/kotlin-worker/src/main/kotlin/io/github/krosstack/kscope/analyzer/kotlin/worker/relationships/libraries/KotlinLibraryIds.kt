package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.libraries

import io.github.krosstack.kscope.model.DeclaredParameterType
import io.github.krosstack.kscope.model.MethodDisambiguator
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

private const val CONSTRUCTOR = "<init>"
private const val UNKNOWN_TYPE = "?"

/**
 * Names library declarations seen through the Analysis API. There is no syntax to read, so the declared parameter
 * types are taken from the symbol; the descriptor grammar is the one of workspace declarations.
 */
internal class KotlinLibraryIds(private val session: KaSession) {
    fun idOf(symbol: KaDeclarationSymbol): SymbolId? = with(session) {
        val descriptor = descriptorOf(symbol) ?: return null
        val origin = LibraryOrigins.of(symbol.containingModule)
        SymbolIdentity.library(origin.ecosystem, origin.coordinate, descriptor).toSymbolId()
    }

    private fun descriptorOf(symbol: KaDeclarationSymbol): SymbolDescriptor? = when (symbol) {
        is KaClassLikeSymbol -> symbol.classId?.let(::classDescriptor)
        is KaConstructorSymbol -> symbol.containingClassId?.let { constructorDescriptor(it, symbol) }
        is KaCallableSymbol -> callableDescriptor(symbol)
        else -> null
    }

    private fun constructorDescriptor(owner: ClassId, symbol: KaConstructorSymbol): SymbolDescriptor =
        classDescriptor(owner).method(CONSTRUCTOR, disambiguator(symbol))

    private fun callableDescriptor(symbol: KaCallableSymbol): SymbolDescriptor? {
        val id = symbol.callableId ?: return null
        val owner = id.classId?.let(::classDescriptor) ?: packageDescriptor(id.packageName)
        val name = id.callableName.asString()
        val isMethodLike = symbol is KaFunctionSymbol || symbol.receiverParameter != null
        return if (isMethodLike) owner.method(name, disambiguator(symbol)) else owner.term(name)
    }

    private fun classDescriptor(id: ClassId): SymbolDescriptor = id.relativeClassName.pathSegments()
        .fold(packageDescriptor(id.packageFqName)) { descriptor, name -> descriptor.type(name.asString()) }

    private fun packageDescriptor(name: FqName): SymbolDescriptor = name.pathSegments()
        .fold(SymbolDescriptor()) { descriptor, segment -> descriptor.namespace(segment.asString()) }

    private fun disambiguator(symbol: KaCallableSymbol): String {
        val receiver = symbol.receiverParameter?.returnType?.let(::typeName)
        val parameters = (symbol as? KaFunctionSymbol)?.valueParameters.orEmpty().map {
            val name = if (it.isVararg) elementName(it.returnType) else typeName(it.returnType)
            DeclaredParameterType(name, it.isVararg)
        }
        return MethodDisambiguator.render(receiver, parameters)
    }

    private fun elementName(arrayType: KaType): String {
        val argument = (arrayType as? KaClassType)?.typeArguments?.firstOrNull()?.type
        return argument?.let(::typeName) ?: typeName(arrayType).removeSuffix("Array")
    }

    private fun typeName(type: KaType): String = when (type) {
        is KaFunctionType -> MethodDisambiguator.FUNCTION_TYPE
        is KaClassType -> type.classId.shortClassName.asString()
        is KaTypeParameterType -> type.name.asString()
        is KaFlexibleType -> typeName(type.lowerBound)
        is KaDefinitelyNotNullType -> typeName(type.original)
        else -> UNKNOWN_TYPE
    }
}
