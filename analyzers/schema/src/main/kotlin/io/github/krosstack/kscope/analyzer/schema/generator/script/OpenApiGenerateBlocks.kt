package io.github.krosstack.kscope.analyzer.schema.generator.script

private const val BLOCK = "openApiGenerate"

/**
 * Reads the `openApiGenerate` blocks of a build script. Each `inputSpec` that a block gives as a path is an input, with
 * the settings of its block; the settings are read from the calls at the top of the block, in the forms of
 * [ScriptCalls], and the last of a setting counts.
 */
internal class OpenApiGenerateBlocks(private val script: ScriptTokens, private val calls: ScriptCalls) {
    fun inputs(): List<ScriptInput> = script.blocksNamed(BLOCK).flatMap(::inputsOf)

    private fun inputsOf(block: ScriptBlock): List<ScriptInput> {
        val given = calls.within(block.interior).filter { it.name.text in OpenApiSettings.names }
        val settings = OpenApiSettings(given.associate { it.name.text to plainValue(it) })
        return given.filter { it.name.text == OpenApiSettings.INPUT_SPEC }.mapNotNull { call ->
            call.arguments.singleOrNull()?.let { ScriptLiterals.path(it.text) }?.let { path ->
                ScriptInput(InputKind.OPENAPI_SPEC, path, call.span, call.name.span, settings)
            }
        }
    }

    private fun plainValue(call: ScriptCall): String? =
        call.arguments.singleOrNull()?.let { ScriptLiterals.plain(it.text) }
}
