package io.github.krosstack.kscope.analyzer.compose

/**
 * The kinds of resource that have a generated accessor. [id] names the kind of symbol, [holder] the nested object of
 * `Res` that the accessors hang off, and [accessorType] the type of the accessor in `org.jetbrains.compose.resources`.
 */
internal enum class ResourceType(val id: String, val holder: String, val accessorType: String) {
    STRING("string", "string", "StringResource"),
    PLURALS("plurals", "plurals", "PluralStringResource"),
    STRING_ARRAY("string-array", "array", "StringArrayResource"),
    DRAWABLE("drawable", "drawable", "DrawableResource"),
    FONT("font", "font", "FontResource"),
    ;

    val kindId: String get() = "compose.resource.$id"

    companion object {
        /** The resource that an element of a `values` file defines, by the name of the element. */
        fun ofValuesElement(tag: String): ResourceType? = when (tag) {
            "string" -> STRING
            "plurals" -> PLURALS
            "string-array" -> STRING_ARRAY
            else -> null
        }
    }
}
