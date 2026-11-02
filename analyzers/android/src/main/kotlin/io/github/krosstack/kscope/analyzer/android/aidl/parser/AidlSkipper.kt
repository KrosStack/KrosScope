package io.github.krosstack.kscope.analyzer.android.aidl.parser

private val nestedKeywords = setOf("parcelable", "interface", "enum", "union")

/** Leaves out the declarations that the parser does not read and keeps a list of them, for the file to report. */
internal class AidlSkipper(private val cursor: AidlCursor) {
    val skipped = mutableListOf<AidlSkipped>()

    /** True when the cursor stands at a type declared inside an interface or a parcelable. */
    fun isNestedDeclaration(): Boolean = cursor.current.text in nestedKeywords

    /** Skips the declaration that starts at the cursor with its keyword, up to the end of its braces or its `;`. */
    fun skipDeclaration() {
        val keyword = cursor.take()
        val name = cursor.expectName("a name")
        while (!cursor.isAtEnd && !cursor.isSymbol("{") && !cursor.isSymbol(";")) cursor.take()
        if (cursor.isSymbol("{")) cursor.skipBalanced("{", "}") else cursor.expectSymbol(";")
        skipped += AidlSkipped(keyword.text, name, cursor.rangeFrom(keyword))
    }
}
