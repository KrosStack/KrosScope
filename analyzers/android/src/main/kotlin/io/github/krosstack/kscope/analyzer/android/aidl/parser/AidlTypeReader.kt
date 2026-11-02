package io.github.krosstack.kscope.analyzer.android.aidl.parser

private val openingBrackets = setOf("(", "[", "{")
private val closingBrackets = setOf(")", "]", "}")

/** Reads the pieces that interfaces and parcelables share: types, constants and the text of expressions. */
internal class AidlTypeReader(private val cursor: AidlCursor) {
    fun type(): AidlType {
        cursor.skipAnnotations()
        val first = cursor.current
        val name = qualifiedName("a type")
        val arguments = typeArguments()
        var dimensions = 0
        while (cursor.isSymbol("[")) {
            cursor.skipBalanced("[", "]")
            dimensions++
        }
        return AidlType(name, arguments, dimensions, cursor.rangeFrom(first))
    }

    fun qualifiedName(description: String): String {
        val parts = mutableListOf(cursor.expectName(description).text)
        while (cursor.acceptSymbol(".")) parts += cursor.expectName(description).text
        return parts.joinToString(".")
    }

    fun constant(): AidlConstant {
        val first = cursor.take()
        val type = type()
        val name = cursor.expectName("a constant name")
        cursor.expectSymbol("=")
        val value = expression()
        cursor.expectSymbol(";")
        return AidlConstant(name, type, value, cursor.rangeFrom(first))
    }

    /** The tokens up to the next `;` outside brackets, as written. The `;` is left for the caller. */
    fun expression(): String {
        val first = cursor.current
        var depth = 0
        var count = 0
        while (!cursor.isAtEnd && !(depth == 0 && cursor.isSymbol(";"))) {
            depth += bracketChange()
            cursor.take()
            count++
        }
        if (count == 0) cursor.fail("Expected a value")
        return if (count == 0) "" else cursor.text.substring(first.start, cursor.previous.end)
    }

    private fun bracketChange(): Int = when (cursor.current.text) {
        in openingBrackets -> 1
        in closingBrackets -> -1
        else -> 0
    }

    private fun typeArguments(): List<AidlType> {
        if (!cursor.acceptSymbol("<")) return emptyList()
        val arguments = mutableListOf(type())
        while (cursor.acceptSymbol(",")) arguments += type()
        cursor.expectSymbol(">")
        return arguments
    }
}
