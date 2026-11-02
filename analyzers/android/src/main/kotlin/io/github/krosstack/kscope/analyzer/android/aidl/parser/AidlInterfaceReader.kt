package io.github.krosstack.kscope.analyzer.android.aidl.parser

/** Reads an `interface`: its constants and its methods with their parameters. */
internal class AidlInterfaceReader(
    private val cursor: AidlCursor,
    private val types: AidlTypeReader,
    private val skipper: AidlSkipper,
) {
    fun declaration(first: AidlToken, isOneway: Boolean): AidlInterface {
        cursor.take()
        val name = cursor.expectName("an interface name")
        cursor.expectSymbol("{")
        val methods = mutableListOf<AidlMethod>()
        val constants = mutableListOf<AidlConstant>()
        while (!cursor.isAtEnd && !cursor.isSymbol("}")) {
            cursor.skipAnnotations()
            when {
                cursor.isWord("const") -> constants += types.constant()
                skipper.isNestedDeclaration() -> skipper.skipDeclaration()
                else -> methods += method()
            }
        }
        cursor.expectSymbol("}")
        return AidlInterface(name, isOneway, methods, constants, cursor.rangeFrom(first))
    }

    private fun method(): AidlMethod {
        val first = cursor.current
        val isOneway = cursor.acceptWord("oneway")
        val returnType = types.type()
        val name = cursor.expectName("a method name")
        cursor.expectSymbol("(")
        val parameters = parameters()
        cursor.expectSymbol(")")
        if (cursor.acceptSymbol("=")) types.expression()
        cursor.expectSymbol(";")
        return AidlMethod(name, returnType, parameters, isOneway, cursor.rangeFrom(first))
    }

    private fun parameters(): List<AidlParameter> {
        val parameters = mutableListOf<AidlParameter>()
        if (cursor.isSymbol(")")) return parameters
        do {
            parameters += parameter()
        } while (cursor.acceptSymbol(","))
        return parameters
    }

    private fun parameter(): AidlParameter {
        cursor.skipAnnotations()
        val direction = AidlDirection.entries.firstOrNull { cursor.isWord(it.keyword) }?.also { cursor.take() }
        val type = types.type()
        return AidlParameter(direction, type, cursor.expectName("a parameter name"))
    }
}
