package io.github.krosstack.kscope.analyzer.schema.web

/**
 * A bundler whose config is read, in the order in which a package that has several configs is read by, with the
 * directory it writes to when nothing says otherwise; Rollup has no such directory.
 */
internal enum class Bundler(val id: String, val defaultDirectory: String?) {
    VITE("vite", "dist"),
    WEBPACK("webpack", "dist"),
    ROLLUP("rollup", null),
    ;

    companion object {
        fun byId(id: String): Bundler? = entries.firstOrNull { it.id == id }
    }
}
