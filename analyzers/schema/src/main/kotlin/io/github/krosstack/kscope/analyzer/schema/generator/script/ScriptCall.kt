package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken

/** A block of braces: the indices of the tokens inside it, and the index after its closing brace. */
internal class ScriptBlock(val interior: IntRange, val end: Int)

/**
 * A call of a build script: the [name] it is made with, its [arguments] with null for any that is not one string
 * literal, the [span] of the call, the index [end] after it and the [block] that follows it, if it has one. An
 * assignment, a `set` call and a call of Groovy without parentheses are calls with one argument as well.
 */
internal class ScriptCall(
    val name: SourceToken,
    val arguments: List<SourceToken?>,
    val span: Span,
    val end: Int,
    val block: ScriptBlock? = null,
)
