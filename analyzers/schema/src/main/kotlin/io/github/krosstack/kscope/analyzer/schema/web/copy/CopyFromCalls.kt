package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptCall
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptCalls
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptLiterals

private const val FROM = "from"
private const val INTO = "into"

/**
 * Reads the `from` calls at the top of a copy block, in the forms of [ScriptCalls]. Every argument that is a literal
 * path is a source, with the paths of [ScriptLiterals]; a `from` of a file collection, a function or a template is not
 * read. The destination of a source is the `into` of the block of its own `from`, else the `into` of the copy block,
 * and the last `into` of a block counts. A destination that is not a literal is none.
 */
internal class CopyFromCalls(private val calls: ScriptCalls) {
    fun sourcesIn(copy: CopyBlock): List<CopySource> {
        val inside = calls.within(copy.block.interior)
        val outer = destinationIn(inside)
        return inside.filter { it.name.text == FROM }.flatMap { sourcesOf(copy.task, it, outer) }
    }

    private fun sourcesOf(task: String, from: ScriptCall, outer: String?): List<CopySource> {
        val own = from.block?.let { destinationIn(calls.within(it.interior)) }
        return from.arguments.mapNotNull { argument -> argument?.let { ScriptLiterals.path(it.text) } }
            .map { path -> CopySource(task, path, from.span, from.name.span, own ?: outer) }
    }

    private fun destinationIn(inside: List<ScriptCall>): String? =
        inside.lastOrNull { it.name.text == INTO }?.arguments?.singleOrNull()?.let { ScriptLiterals.plain(it.text) }
}
