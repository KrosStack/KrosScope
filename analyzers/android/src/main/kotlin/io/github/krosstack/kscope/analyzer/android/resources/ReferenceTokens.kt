package io.github.krosstack.kscope.analyzer.android.resources

/** What an attribute value or a text says about resources. */
internal sealed interface ReferenceToken {
    /**
     * A reference to [key]; [start] is where the token starts in the value and [nameStart] where the name starts.
     * [declares] is true for `@+id/name`.
     */
    data class Resource(val key: ResourceKey, val start: Int, val nameStart: Int, val declares: Boolean) :
        ReferenceToken

    /** A reference to the framework or to another package, which the workspace cannot answer. */
    data object External : ReferenceToken

    data object None : ReferenceToken
}

private val syntax = Regex("""([@?])(\+)?\*?(?:([A-Za-z_][\w.]*):)?(?:([a-z][a-z-]*)/)?([\w.$]+)""")

private const val ATTRIBUTE_TYPE = "attr"
private const val SIGIL_GROUP = 1
private const val PLUS_GROUP = 2
private const val PACKAGE_GROUP = 3
private const val TYPE_GROUP = 4
private const val NAME_GROUP = 5

internal object ReferenceTokens {
    /** Reads `@type/name`, `@+id/name`, `?attr/name` and `?name`, with an optional package such as `android:`. */
    fun parse(raw: String): ReferenceToken {
        val match = syntax.matchEntire(raw.trim()) ?: return ReferenceToken.None
        val isAttribute = match.groupValues[SIGIL_GROUP] == "?"
        val type = match.groupValues[TYPE_GROUP].ifEmpty { if (isAttribute) ATTRIBUTE_TYPE else "" }
        val isReference = type.isNotEmpty() && (!isAttribute || type == ATTRIBUTE_TYPE)
        val start = raw.indexOfFirst { !it.isWhitespace() }
        return when {
            !isReference -> ReferenceToken.None

            match.groupValues[PACKAGE_GROUP].isNotEmpty() -> ReferenceToken.External

            else -> ReferenceToken.Resource(
                key = ResourceKey(type, match.groupValues[NAME_GROUP]),
                start = start,
                nameStart = start + checkNotNull(match.groups[NAME_GROUP]).range.first,
                declares = match.groupValues[PLUS_GROUP].isNotEmpty(),
            )
        }
    }
}
