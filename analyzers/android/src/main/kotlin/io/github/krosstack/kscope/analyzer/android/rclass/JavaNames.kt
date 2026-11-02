package io.github.krosstack.kscope.analyzer.android.rclass

internal object JavaNames {
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private val keywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_",
    )

    fun isIdentifier(name: String): Boolean = identifier.matches(name) && name !in keywords

    fun isPackage(name: String): Boolean = name.isNotEmpty() && name.split('.').all(::isIdentifier)
}
