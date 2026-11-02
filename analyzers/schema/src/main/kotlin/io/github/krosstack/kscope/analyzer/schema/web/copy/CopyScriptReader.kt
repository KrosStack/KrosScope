package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptCalls
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptTokens
import io.github.krosstack.kscope.analyzer.schema.lexing.KotlinTokenizer

private const val FROM = "from"
private val blockWords = listOf("processResources", "copy", "sync", "named", "register", "task")

/**
 * Reads the paths that the Gradle tasks of a build script copy from, in Kotlin or in Groovy, from the tokens of the
 * script: comments and strings are never taken for code. The paths are the literals that the `from` calls of the copy
 * blocks of [CopyBlocks] name, with the prefixes of `$rootDir` and `$projectDir` that the paths of generators allow.
 * What a `Copy` or a `Sync` task does is not evaluated: a `from` that a condition, a loop or a function guards
 * counts as any other, `include`, `exclude` and `rename` do not narrow a source, and the files that another task, a
 * plugin or a convention adds to a task are unknown. A script is only split into tokens when it can have a source:
 * when it mentions a `from` and a word that starts a copy block.
 */
internal class CopyScriptReader(text: String) {
    private val script = ScriptTokens(KotlinTokenizer(text).tokens())
    private val blocks = CopyBlocks(script)
    private val froms = CopyFromCalls(ScriptCalls(script))

    fun sources(): List<CopySource> = blocks.all().flatMap(froms::sourcesIn).sortedBy { it.call.start }

    companion object {
        fun mayHaveSources(text: String): Boolean = FROM in text && blockWords.any { it in text }
    }
}
