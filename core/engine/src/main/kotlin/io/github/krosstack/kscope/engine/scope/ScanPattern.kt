package io.github.krosstack.kscope.engine.scope

/**
 * A path pattern relative to the workspace root. Segments are separated by `/`; `*` and `?` stand for characters
 * inside one segment, a segment `**` for any number of segments, and every other character for itself, in letter
 * case. A pattern also matches everything below a directory it matches. Matching takes time proportional to the
 * product of the number of segments of the pattern and of the path, and uses no regular expressions.
 */
public class ScanPattern(text: String) {
    private val segments: List<String> = text.split(SEPARATOR)

    public fun matches(path: String): Boolean {
        val parts = path.split(SEPARATOR)
        var reachable = BooleanArray(parts.size + 1).also { it[0] = true }
        for (segment in segments) {
            reachable = if (segment == ANY_SEGMENTS) spread(reachable) else step(reachable, segment, parts)
        }
        return reachable.any { it }
    }

    private fun spread(reachable: BooleanArray): BooleanArray {
        var seen = false
        return BooleanArray(reachable.size) { index ->
            seen = seen || reachable[index]
            seen
        }
    }

    private fun step(reachable: BooleanArray, segment: String, parts: List<String>): BooleanArray =
        BooleanArray(reachable.size) { index ->
            index > 0 && reachable[index - 1] && GlobSegment.matches(segment, parts[index - 1])
        }

    public companion object {
        public const val MAXIMUM_LENGTH: Int = 512

        private const val SEPARATOR = '/'
        private const val BACKSLASH = '\\'
        private const val ANY_SEGMENTS = "**"

        /** Why [pattern] is outside the grammar, or null when it is inside. */
        public fun problemWith(pattern: String): String? = when {
            pattern.isEmpty() -> "must not be empty"
            pattern.length > MAXIMUM_LENGTH -> "must have at most $MAXIMUM_LENGTH characters"
            BACKSLASH in pattern -> "must use / and not \\"
            ':' in pattern -> "must not hold :"
            pattern.startsWith("/") -> "must not start with /"
            pattern.startsWith("./") -> "must not start with ./"
            pattern.endsWith("/") -> "must not end with /"
            else -> segmentProblem(pattern.split(SEPARATOR))
        }

        private fun segmentProblem(segments: List<String>): String? = when {
            segments.any { it.isEmpty() } -> "must not hold an empty segment"
            ".." in segments -> "must not hold a .. segment"
            "." in segments -> "must not hold a . segment"
            segments.any { it != ANY_SEGMENTS && ANY_SEGMENTS in it } -> "** must be a whole segment"
            else -> null
        }
    }
}
