package io.github.krosstack.kscope.analyzer.schema.generator.script

private const val BLOCK = "wire"
private const val SOURCE_PATH = "sourcePath"
private const val SRC_DIR = "srcDir"

/**
 * Reads the `wire` blocks of a build script. An input is a path that `sourcePath` is called with, or that `srcDir` is
 * called with inside a block of `sourcePath`. The `protoPath` of the block only gives what the sources import, and
 * the `include` and `exclude` filters of a source path are not read: every proto file below it counts.
 */
internal class WireBlocks(private val script: ScriptTokens, private val calls: ScriptCalls) {
    fun inputs(): List<ScriptInput> = script.blocksNamed(BLOCK).flatMap(::blockInputs)

    private fun blockInputs(block: ScriptBlock): List<ScriptInput> =
        calls.within(block.interior).filter { it.name.text == SOURCE_PATH }.flatMap { call ->
            callInputs(call) + call.block?.let(::sourceDirectories).orEmpty()
        }

    private fun sourceDirectories(block: ScriptBlock): List<ScriptInput> =
        calls.within(block.interior).filter { it.name.text == SRC_DIR }.flatMap(::callInputs)

    private fun callInputs(call: ScriptCall): List<ScriptInput> = call.arguments.mapNotNull { argument ->
        argument?.let { ScriptLiterals.path(it.text) }?.let { path ->
            ScriptInput(InputKind.WIRE_SOURCE_PATH, path, call.span, call.name.span)
        }
    }
}
