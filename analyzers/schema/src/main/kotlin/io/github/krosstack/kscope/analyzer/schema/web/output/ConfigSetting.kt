package io.github.krosstack.kscope.analyzer.schema.web.output

/**
 * What a bundler config says about one setting. A setting that the config does not mention is [Absent]. A value that
 * is no string literal and no `path.resolve(__dirname, ...)` of literals is [Computed] and not read. A [Literal] made
 * with `__dirname` lies relative to the directory of the config. A [Block] is an object literal, with the range of the
 * tokens between its braces.
 */
internal sealed interface ConfigSetting {
    data object Absent : ConfigSetting

    data object Computed : ConfigSetting

    data class Literal(val text: String, val isConfigRelative: Boolean = false) : ConfigSetting

    data class Block(val interior: IntRange) : ConfigSetting
}
