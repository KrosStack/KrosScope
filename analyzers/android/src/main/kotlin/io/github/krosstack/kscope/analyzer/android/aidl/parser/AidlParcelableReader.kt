package io.github.krosstack.kscope.analyzer.android.aidl.parser

private class ParcelableBody(val fields: List<AidlField>, val constants: List<AidlConstant>)

/** Reads a `parcelable`, unstructured with only a name, or structured with fields and constants. */
internal class AidlParcelableReader(
    private val cursor: AidlCursor,
    private val types: AidlTypeReader,
    private val skipper: AidlSkipper,
) {
    fun declaration(first: AidlToken): AidlParcelable {
        cursor.take()
        val segments = mutableListOf(cursor.expectName("a parcelable name"))
        while (cursor.acceptSymbol(".")) segments += cursor.expectName("a parcelable name")
        if (cursor.isSymbol("<")) cursor.skipBalanced("<", ">")
        val isStructured = cursor.isSymbol("{")
        val body = if (isStructured) structuredBody() else unstructuredBody()
        return AidlParcelable(
            name = segments.last(),
            qualifier = segments.dropLast(1).joinToString(".") { it.text }.ifEmpty { null },
            isStructured = isStructured,
            fields = body.fields,
            constants = body.constants,
            declaration = cursor.rangeFrom(first),
        )
    }

    // `parcelable Foo cpp_header "foo.h";` names the native headers of an unstructured parcelable.
    private fun unstructuredBody(): ParcelableBody {
        while (!cursor.isAtEnd && !cursor.isSymbol(";")) cursor.take()
        cursor.expectSymbol(";")
        return ParcelableBody(emptyList(), emptyList())
    }

    private fun structuredBody(): ParcelableBody {
        cursor.expectSymbol("{")
        val fields = mutableListOf<AidlField>()
        val constants = mutableListOf<AidlConstant>()
        while (!cursor.isAtEnd && !cursor.isSymbol("}")) {
            cursor.skipAnnotations()
            when {
                cursor.isWord("const") -> constants += types.constant()
                skipper.isNestedDeclaration() -> skipper.skipDeclaration()
                else -> fields += field()
            }
        }
        cursor.expectSymbol("}")
        return ParcelableBody(fields, constants)
    }

    private fun field(): AidlField {
        val first = cursor.current
        val type = types.type()
        val name = cursor.expectName("a field name")
        val defaultValue = if (cursor.acceptSymbol("=")) types.expression() else null
        cursor.expectSymbol(";")
        return AidlField(name, type, defaultValue, cursor.rangeFrom(first))
    }
}
