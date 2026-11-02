package io.github.krosstack.kscope.analyzer.schema.web.output

/**
 * What the search for the config object of a bundler config found: the [Found] opening brace of the object, [Missing]
 * when the file has no export of a config, or [Unidentified] when it has one but the object it holds cannot be told.
 */
internal sealed interface ConfigObjectLookup {
    data class Found(val opening: Int) : ConfigObjectLookup

    data object Missing : ConfigObjectLookup

    data object Unidentified : ConfigObjectLookup
}
