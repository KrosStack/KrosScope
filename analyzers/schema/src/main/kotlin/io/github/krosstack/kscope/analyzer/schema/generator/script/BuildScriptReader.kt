package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.lexing.KotlinTokenizer

private val generatorWords = listOf("openApiGenerate", "wire")

/**
 * Reads the inputs that the code generators of a Gradle build script are given, in Kotlin or in Groovy, from the
 * tokens of the script: comments and strings are never taken for code. The generators are the OpenAPI Generator, by
 * its `openApiGenerate` block, and Wire, by its `wire` block. Only paths that are literals are read, with the prefixes
 * of [ScriptLiterals].
 */
internal class BuildScriptReader(text: String) {
    private val script = ScriptTokens(KotlinTokenizer(text).tokens())
    private val calls = ScriptCalls(script)

    fun inputs(): List<ScriptInput> =
        (OpenApiGenerateBlocks(script, calls).inputs() + WireBlocks(script, calls).inputs()).sortedBy { it.call.start }

    companion object {
        /** A cheap test, before the script is split into tokens, of whether it can have any input. */
        fun mayHaveInputs(text: String): Boolean = generatorWords.any { it in text }
    }
}
