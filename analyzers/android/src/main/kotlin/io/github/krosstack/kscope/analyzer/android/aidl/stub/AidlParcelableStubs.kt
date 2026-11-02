package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.aidl.AidlSymbols
import io.github.krosstack.kscope.analyzer.android.aidl.packageOf
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlField
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParcelable

/** The Java surface of a structured parcelable: a class with its fields, constants and `CREATOR`. */
internal class AidlParcelableStubs(private val file: AidlFile, private val java: AidlJavaTypes) {
    fun plan(declaration: AidlParcelable): StubType {
        val owner = AidlSymbols.typeDescriptor(file.packageOf(declaration), declaration.name.text)
        val name = declaration.name.text
        val creator = StubField(
            name = "CREATOR",
            role = StubRole.CREATOR,
            signature = "public static final android.os.Parcelable.Creator<$name> CREATOR",
            initializer = "null",
            flags = staticFlags,
        )
        return StubType(
            name = name,
            role = StubRole.PARCELABLE,
            signature = "public class $name implements android.os.Parcelable",
            kind = classKind,
            members = declaration.fields.map(::field) +
                declaration.constants.map { constantField(java, it, AidlSymbols.constantDescriptor(owner, it)) } +
                creator,
            flags = emptySet(),
            truth = owner,
        )
    }

    private fun field(field: AidlField) = StubField(
        name = field.name.text,
        role = StubRole.FIELD,
        signature = "public ${java.javaName(field.type)} ${field.name.text}",
        initializer = null,
        flags = emptySet(),
    )
}
