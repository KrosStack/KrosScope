package io.github.krosstack.kscope.analyzer.compose.res

private val hardKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var", "when",
    "while",
)

internal object KotlinNames {
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun isIdentifier(name: String): Boolean = identifier.matches(name)

    fun isPackage(name: String): Boolean =
        name.isNotEmpty() && name.split('.').all { isIdentifier(it) && it !in hardKeywords }

    /** The name as Kotlin source writes it: a keyword is quoted. */
    fun written(name: String): String = if (name in hardKeywords) "`$name`" else name
}
