package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlConstant
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlDeclaration
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlInterface
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlMethod
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParcelable
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlType
import io.github.krosstack.kscope.model.DeclaredParameterType
import io.github.krosstack.kscope.model.MethodDisambiguator
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind

/**
 * The identity of an AIDL symbol: its module, its source set and a descriptor below the namespace `aidl` that follows
 * the package of the declaration. The namespace keeps it apart from the Java surface that is generated from it.
 */
internal object AidlSymbols {
    const val INTERFACE_KIND = "aidl.interface"
    const val METHOD_KIND = "aidl.method"
    const val CONSTANT_KIND = "aidl.constant"
    const val PARCELABLE_KIND = "aidl.parcelable"
    private const val NAMESPACE = "aidl"

    val interfaceKind = SymbolKind(INTERFACE_KIND, SymbolCategory.CONTRACT)
    val methodKind = SymbolKind(METHOD_KIND, SymbolCategory.CONTRACT)
    val constantKind = SymbolKind(CONSTANT_KIND, SymbolCategory.CONTRACT)
    val parcelableKind = SymbolKind(PARCELABLE_KIND, SymbolCategory.CONTRACT)

    fun typeDescriptor(packageName: String?, name: String): SymbolDescriptor {
        val root = SymbolDescriptor().namespace(NAMESPACE)
        return packageSegments(packageName).fold(root) { descriptor, segment -> descriptor.namespace(segment) }
            .type(name)
    }

    fun methodDescriptor(owner: SymbolDescriptor, method: AidlMethod): SymbolDescriptor =
        owner.method(method.name.text, disambiguatorOf(method.parameters.map { it.type }))

    fun constantDescriptor(owner: SymbolDescriptor, constant: AidlConstant): SymbolDescriptor =
        owner.term(constant.name.text)

    fun idOf(module: ModuleId, sourceSetName: String, descriptor: SymbolDescriptor): SymbolId =
        SymbolIdentity.workspace(module, sourceSetName, descriptor).toSymbolId()

    /** The declared parameter types by simple name, with `[]` for each dimension, as Java declarations show them. */
    fun disambiguatorOf(parameterTypes: List<AidlType>): String =
        MethodDisambiguator.render(null, parameterTypes.map { DeclaredParameterType(simpleNameOf(it)) })

    fun packageSegments(packageName: String?): List<String> =
        packageName.orEmpty().split('.').filter { it.isNotEmpty() }

    private fun simpleNameOf(type: AidlType): String = type.simpleName + "[]".repeat(type.arrayDimensions)
}

/** The package a declaration belongs to: the one written in front of an unstructured parcelable, else the file's. */
internal fun AidlFile.packageOf(declaration: AidlDeclaration): String? =
    (declaration as? AidlParcelable)?.qualifier ?: packageName

internal fun AidlFile.qualifiedNameOf(declaration: AidlDeclaration): String =
    listOfNotNull(packageOf(declaration), declaration.name.text).joinToString(".")

internal fun AidlMethod.signature(): String {
    val parameterList = parameters.joinToString(", ") { parameter ->
        listOfNotNull(parameter.direction?.keyword, parameter.type.text(), parameter.name.text).joinToString(" ")
    }
    return (if (isOneway) "oneway " else "") + "${returnType.text()} ${name.text}($parameterList)"
}

internal fun AidlInterface.signature(): String = (if (isOneway) "oneway " else "") + "interface ${name.text}"

internal fun AidlConstant.signature(): String = "const ${type.text()} ${name.text} = $value"

internal fun AidlParcelable.signature(): String = "parcelable ${name.text}"
