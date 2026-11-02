package io.github.krosstack.kscope.analyzer.schema.web.map

/**
 * What a source map says about the file it maps and where the files it was built from lie: the optional [file], the
 * [sourceRoot] that is put before each entry of [sources], and those entries as they are written.
 */
internal class SourceMap(val file: String?, val sourceRoot: String, val sources: List<String>)
