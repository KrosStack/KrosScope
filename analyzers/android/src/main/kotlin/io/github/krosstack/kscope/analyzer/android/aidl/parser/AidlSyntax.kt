package io.github.krosstack.kscope.analyzer.android.aidl.parser

import io.github.krosstack.kscope.model.TextRange

/** A name as written in an `.aidl` file, with the range of its characters. */
internal data class AidlName(val text: String, val range: TextRange)

/**
 * A type as written: its possibly qualified [name], the type [arguments] of `List<String>` and the number of array
 * dimensions. The size of a fixed-size array is not kept.
 */
internal data class AidlType(
    val name: String,
    val arguments: List<AidlType>,
    val arrayDimensions: Int,
    val range: TextRange,
) {
    val simpleName: String get() = name.substringAfterLast('.')

    /** The type as the declarations of the file show it, such as `Map<String, Foo>[]`. */
    fun text(): String {
        val typeArguments = if (arguments.isEmpty()) "" else arguments.joinToString(", ", "<", ">") { it.text() }
        return name + typeArguments + "[]".repeat(arrayDimensions)
    }
}

internal enum class AidlDirection(val keyword: String) {
    IN("in"),
    OUT("out"),
    INOUT("inout"),
}

internal data class AidlParameter(val direction: AidlDirection?, val type: AidlType, val name: AidlName)

internal data class AidlMethod(
    val name: AidlName,
    val returnType: AidlType,
    val parameters: List<AidlParameter>,
    val isOneway: Boolean,
    val declaration: TextRange,
)

/** A `const` of an interface or a parcelable; [value] is the expression as written. */
internal data class AidlConstant(val name: AidlName, val type: AidlType, val value: String, val declaration: TextRange)

internal data class AidlField(
    val name: AidlName,
    val type: AidlType,
    val defaultValue: String?,
    val declaration: TextRange,
)

internal data class AidlImport(val name: String, val range: TextRange)

internal sealed interface AidlDeclaration {
    val name: AidlName
    val declaration: TextRange
}

internal data class AidlInterface(
    override val name: AidlName,
    val isOneway: Boolean,
    val methods: List<AidlMethod>,
    val constants: List<AidlConstant>,
    override val declaration: TextRange,
) : AidlDeclaration

/**
 * A `parcelable`. An unstructured one, `parcelable Foo;`, only names a class that user code implements. A structured
 * one has [fields] and the generator makes its class. [qualifier] is the package written in front of the name of an
 * unstructured parcelable, as in `parcelable android.os.Bundle;`.
 */
internal data class AidlParcelable(
    override val name: AidlName,
    val qualifier: String?,
    val isStructured: Boolean,
    val fields: List<AidlField>,
    val constants: List<AidlConstant>,
    override val declaration: TextRange,
) : AidlDeclaration

/** A declaration that the parser recognises and leaves out: an `enum`, a `union` or a type nested in another. */
internal data class AidlSkipped(val keyword: String, val name: AidlName, val declaration: TextRange)

internal data class AidlFile(
    val packageName: String?,
    val imports: List<AidlImport>,
    val declarations: List<AidlDeclaration>,
    val skipped: List<AidlSkipped>,
)

/** Why a file could not be read, and where. */
internal data class AidlProblem(val message: String, val range: TextRange)

internal sealed interface AidlParseResult {
    data class Parsed(val file: AidlFile) : AidlParseResult

    data class Malformed(val problem: AidlProblem) : AidlParseResult
}
