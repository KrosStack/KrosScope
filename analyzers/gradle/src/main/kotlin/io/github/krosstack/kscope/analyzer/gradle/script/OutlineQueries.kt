package io.github.krosstack.kscope.analyzer.gradle.script

import io.github.krosstack.kscope.spi.worker.ScriptArgument
import io.github.krosstack.kscope.spi.worker.ScriptCall

/** The calls of the blocks with this name that are not called on a receiver. */
internal fun List<ScriptCall>.blocks(name: String): List<ScriptCall> = filter { it.name == name && it.receiver == null }

/** The statements inside every block with this name. */
internal fun List<ScriptCall>.bodies(name: String): List<ScriptCall> = blocks(name).flatMap { it.body }

internal fun ScriptCall.positional(): List<ScriptArgument> = arguments.filter { it.name == null }

internal fun ScriptCall.firstLiteral(): String? = positional().firstOrNull()?.literal

internal fun ScriptCall.firstExpression(): String? = positional().firstOrNull()?.expression

internal fun ScriptCall.namedArgument(name: String): ScriptArgument? = arguments.firstOrNull { it.name == name }

internal fun ScriptCall.child(name: String): ScriptCall? = body.firstOrNull { it.receiver == null && it.name == name }

internal fun List<ScriptCall>.allCalls(): List<ScriptCall> = flatMap { listOf(it) + it.body.allCalls() }
