package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.Span
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptBlock
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptPath

/** A block of a build script that configures what a Gradle task copies: the name of the [task] and its [block]. */
internal class CopyBlock(val task: String, val block: ScriptBlock)

/**
 * A path that a `from` call of a copy block names: the [task] of the block, the [path] as the script writes it, the
 * span of the [call] and of the [name] it is made with, and the destination that the `into` of the call or of its
 * block gives, if that is a literal.
 */
internal class CopySource(val task: String, val path: ScriptPath, val call: Span, val name: Span, val into: String?)
