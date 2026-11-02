package io.github.krosstack.kscope.analyzer.schema.generator.script

import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken

/**
 * A call of a build script that a block follows, `receiver.name<types>(arguments) { block }`: the [name] it is made
 * with, the text of the token before the dot of its [receiver], which is null when the call has none, the tokens of its
 * [typeArguments], and its [arguments], each as the tokens between the commas.
 */
internal class QualifiedCall(
    val name: SourceToken,
    val receiver: String?,
    val typeArguments: List<SourceToken>,
    val arguments: List<List<SourceToken>>,
    val block: ScriptBlock,
)
