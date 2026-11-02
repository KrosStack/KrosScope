package io.github.krosstack.kscope.analyzer.schema.stub

import io.github.krosstack.kscope.model.DeclaredParameterType
import io.github.krosstack.kscope.model.MethodDisambiguator

private val hardKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof", "val", "var",
    "when", "while",
)

private val plainIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

/** Names as the analyzer reads them from the syntax of a stub, so that the ids of stub symbols match its ids. */
internal object StubTypes {
    /**
     * The simple name of a declared type, as the analyzer puts it in the disambiguator of a function: no package, type
     * arguments or nullability mark, and `Function` for every function type.
     */
    fun simpleName(type: String): String {
        val withoutNullability = type.trim().removeSuffix("?")
        return if ("->" in withoutNullability) {
            MethodDisambiguator.FUNCTION_TYPE
        } else {
            withoutNullability.substringBefore('<').substringAfterLast('.')
        }
    }

    /** The disambiguator of a function or constructor with parameters of these types. */
    fun disambiguator(parameterTypes: List<String>): String = MethodDisambiguator.render(
        null,
        parameterTypes.map { DeclaredParameterType(simpleName(it)) },
    )

    /** The name as written in source: in backticks when it is a keyword or not a plain identifier. */
    fun written(name: String): String = if (name in hardKeywords || !plainIdentifier.matches(name)) "`$name`" else name
}
