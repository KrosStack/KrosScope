package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlConstant
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlDeclaration
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlInterface
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParcelable
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolKind

internal val interfaceKind = SymbolKind("java.interface", SymbolCategory.TYPE)
internal val classKind = SymbolKind("java.class", SymbolCategory.TYPE)
internal val staticFlags = setOf(SymbolFlag.STATIC)

private val defaultValues = mapOf(
    "boolean" to "false",
    "char" to "'\\0'",
    "byte" to "0",
    "int" to "0",
    "long" to "0",
    "float" to "0",
    "double" to "0",
)

/**
 * Plans the Java surface that the AIDL generator makes for a declaration, from its documented template: for an
 * interface, the interface with a `DESCRIPTOR`, its constants and methods, a `Default` implementation and an abstract
 * `Stub` with `asInterface` and `asBinder`; for a structured parcelable, a class with its fields. An unstructured
 * parcelable has no surface, because user code implements it.
 */
internal class AidlStubPlanner(file: AidlFile) {
    private val java = AidlJavaTypes(file)
    private val interfaces = AidlInterfaceStubs(file, java)
    private val parcelables = AidlParcelableStubs(file, java)

    fun plan(declaration: AidlDeclaration): StubType? = when (declaration) {
        is AidlInterface -> interfaces.plan(declaration)
        is AidlParcelable -> declaration.takeIf { it.isStructured }?.let(parcelables::plan)
    }
}

/** The constant of an interface or a parcelable as a `public static final` field with a default value. */
internal fun constantField(java: AidlJavaTypes, constant: AidlConstant, truth: SymbolDescriptor) = StubField(
    name = constant.name.text,
    role = StubRole.CONSTANT,
    signature = "public static final ${java.javaName(constant.type)} ${constant.name.text}",
    initializer = defaultValues[constant.type.name]?.takeIf { constant.type.arrayDimensions == 0 } ?: "null",
    flags = staticFlags,
    truth = truth,
)
