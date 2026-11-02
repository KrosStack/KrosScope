package io.github.krosstack.kscope.benchmark.workspace

/** One end of a gold relationship, in the terms of [LinkEnd]. */
internal sealed interface GoldEnd {
    fun accepts(end: LinkEnd): Boolean

    /** A file with the lines that count; a file alone, without lines, stands for any line of it. */
    data class InFile(val file: String, val lines: Set<Int>) : GoldEnd {
        override fun accepts(end: LinkEnd): Boolean =
            end is LinkEnd.At && end.place.file == file && (lines.isEmpty() || end.place.line in lines)
    }

    data class InModule(val repository: String, val path: String) : GoldEnd {
        override fun accepts(end: LinkEnd): Boolean =
            end is LinkEnd.OfModule && end.repository == repository && end.path == path
    }
}
