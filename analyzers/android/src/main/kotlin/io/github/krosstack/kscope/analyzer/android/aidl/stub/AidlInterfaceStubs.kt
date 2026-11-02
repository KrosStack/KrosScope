package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.aidl.AidlSymbols
import io.github.krosstack.kscope.analyzer.android.aidl.packageOf
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlInterface
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlMethod
import io.github.krosstack.kscope.analyzer.android.aidl.qualifiedNameOf
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolFlag

private const val REMOTE_EXCEPTION = "android.os.RemoteException"
private const val BINDER = "android.os.IBinder"
private const val RETURN_NULL = "{ return null; }"

/** The Java surface of an interface: the interface itself, its `Default` implementation and its `Stub`. */
internal class AidlInterfaceStubs(private val file: AidlFile, private val java: AidlJavaTypes) {
    fun plan(declaration: AidlInterface): StubType {
        val owner = AidlSymbols.typeDescriptor(file.packageOf(declaration), declaration.name.text)
        val qualified = file.qualifiedNameOf(declaration)
        val members = listOf(descriptorField(qualified)) +
            declaration.constants.map { constantField(java, it, AidlSymbols.constantDescriptor(owner, it)) } +
            declaration.methods.map { method(it, AidlSymbols.methodDescriptor(owner, it)) } +
            listOf(defaultType(qualified), stubType(qualified, owner))
        return StubType(
            name = declaration.name.text,
            role = StubRole.INTERFACE,
            signature = "public interface ${declaration.name.text} extends android.os.IInterface",
            kind = interfaceKind,
            members = members,
            flags = emptySet(),
            truth = owner,
        )
    }

    private fun descriptorField(qualified: String) = StubField(
        name = "DESCRIPTOR",
        role = StubRole.DESCRIPTOR,
        signature = "public static final java.lang.String DESCRIPTOR",
        initializer = "\"$qualified\"",
        flags = staticFlags,
    )

    private fun method(method: AidlMethod, truth: SymbolDescriptor): StubMethod {
        val parameters = method.parameters.joinToString(", ") { "${java.javaName(it.type)} ${it.name.text}" }
        return StubMethod(
            name = method.name.text,
            role = StubRole.METHOD,
            signature = "public ${java.javaName(method.returnType)} ${method.name.text}($parameters) " +
                "throws $REMOTE_EXCEPTION",
            disambiguator = AidlSymbols.disambiguatorOf(method.parameters.map { it.type }),
            body = null,
            flags = setOf(SymbolFlag.ABSTRACT),
            truth = truth,
        )
    }

    private fun defaultType(qualified: String) = StubType(
        name = "Default",
        role = StubRole.DEFAULT,
        signature = "public static class Default implements $qualified",
        kind = classKind,
        members = emptyList(),
        flags = staticFlags,
    )

    private fun stubType(qualified: String, owner: SymbolDescriptor) = StubType(
        name = "Stub",
        role = StubRole.STUB,
        signature = "public static abstract class Stub extends android.os.Binder implements $qualified",
        kind = classKind,
        members = listOf(asInterface(qualified), asBinder()),
        flags = staticFlags + SymbolFlag.ABSTRACT,
        truth = owner,
    )

    private fun asInterface(qualified: String) = StubMethod(
        name = "asInterface",
        role = StubRole.AS_INTERFACE,
        signature = "public static $qualified asInterface($BINDER obj)",
        disambiguator = "IBinder",
        body = RETURN_NULL,
        flags = staticFlags,
    )

    private fun asBinder() = StubMethod(
        name = "asBinder",
        role = StubRole.AS_BINDER,
        signature = "public $BINDER asBinder()",
        disambiguator = "",
        body = RETURN_NULL,
        flags = emptySet(),
    )
}
