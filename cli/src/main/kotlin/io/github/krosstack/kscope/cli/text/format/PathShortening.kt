package io.github.krosstack.kscope.cli.text.format

private const val ELLIPSIS = "..."
private const val GAP = "/$ELLIPSIS/"
private const val SEPARATOR = '/'

/** Fits a path into a width by replacing the middle of it, so that the start and the file name stay readable. */
internal object PathShortening {
    /** [path] unchanged when it fits [limit]; otherwise its first directories, `...` and its last segment. */
    fun shorten(path: String, limit: Int): String {
        val segments = path.split(SEPARATOR)
        if (path.length <= limit || segments.size == 1) return path
        val tail = segments.last()
        val head = leadingDirectories(segments.dropLast(1), limit - GAP.length - tail.length)
        return when {
            head.isNotEmpty() -> head.joinToString("/") + GAP + tail
            tail.length + ELLIPSIS.length + 1 <= limit -> "$ELLIPSIS/$tail"
            else -> ELLIPSIS + tail.takeLast(maxOf(limit - ELLIPSIS.length, 0))
        }
    }

    private fun leadingDirectories(directories: List<String>, room: Int): List<String> {
        val kept = mutableListOf<String>()
        var used = 0
        for (directory in directories) {
            val next = used + directory.length + if (kept.isEmpty()) 0 else 1
            if (next > room) break
            kept += directory
            used = next
        }
        return kept
    }
}
