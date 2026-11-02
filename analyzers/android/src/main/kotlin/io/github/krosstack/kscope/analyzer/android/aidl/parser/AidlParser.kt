package io.github.krosstack.kscope.analyzer.android.aidl.parser

private const val BYTE_ORDER_MARK = "﻿"

/**
 * Reads the declarations of an `.aidl` file: its package, imports, interfaces with their methods and constants, and
 * unstructured and structured parcelables. Annotations are skipped, expressions are kept as text, and `enum`,
 * `union` and nested type declarations are left out and reported. See ADR 0009.
 */
internal object AidlParser {
    fun parse(text: String): AidlParseResult {
        val source = text.removePrefix(BYTE_ORDER_MARK)
        return when (val scan = AidlScanner(source).scan()) {
            is AidlScan.Failed -> AidlParseResult.Malformed(scan.problem)
            is AidlScan.Tokens -> read(AidlCursor(source, scan.tokens))
        }
    }

    private fun read(cursor: AidlCursor): AidlParseResult {
        val file = AidlFileReader(cursor).file()
        val problem = cursor.problem
        return if (problem != null) AidlParseResult.Malformed(problem) else AidlParseResult.Parsed(file)
    }
}

private class AidlFileReader(private val cursor: AidlCursor) {
    private val types = AidlTypeReader(cursor)
    private val skipper = AidlSkipper(cursor)
    private val interfaces = AidlInterfaceReader(cursor, types, skipper)
    private val parcelables = AidlParcelableReader(cursor, types, skipper)

    fun file(): AidlFile {
        cursor.skipAnnotations()
        val packageName = packageName()
        val imports = imports()
        val declarations = mutableListOf<AidlDeclaration>()
        while (!cursor.isAtEnd) declaration()?.let(declarations::add)
        return AidlFile(packageName, imports, declarations, skipper.skipped)
    }

    private fun packageName(): String? {
        if (!cursor.acceptWord("package")) return null
        val name = types.qualifiedName("a package name")
        cursor.expectSymbol(";")
        return name
    }

    private fun imports(): List<AidlImport> {
        val imports = mutableListOf<AidlImport>()
        while (cursor.isWord("import")) {
            val first = cursor.take()
            val name = types.qualifiedName("an import name")
            cursor.expectSymbol(";")
            imports += AidlImport(name, cursor.rangeFrom(first))
        }
        return imports
    }

    private fun declaration(): AidlDeclaration? {
        cursor.skipAnnotations()
        val first = cursor.current
        val isOneway = cursor.acceptWord("oneway")
        return when {
            cursor.isWord("interface") -> interfaces.declaration(first, isOneway)
            !isOneway && cursor.isWord("parcelable") -> parcelables.declaration(first)
            !isOneway && (cursor.isWord("enum") || cursor.isWord("union")) -> null.also { skipper.skipDeclaration() }
            else -> null.also { cursor.fail("Expected 'interface', 'parcelable', 'enum' or 'union'") }
        }
    }
}
