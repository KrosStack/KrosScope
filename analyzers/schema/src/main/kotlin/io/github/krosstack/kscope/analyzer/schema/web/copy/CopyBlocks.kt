package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.generator.script.QualifiedCall
import io.github.krosstack.kscope.analyzer.schema.generator.script.QualifiedCalls
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptLiterals
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptTokens
import io.github.krosstack.kscope.analyzer.schema.lexing.SourceToken
import io.github.krosstack.kscope.analyzer.schema.lexing.TokenKind

private const val PROCESS_RESOURCES = "processResources"
private const val COPY = "copy"
private const val SYNC = "sync"
private const val TASKS = "tasks"
private const val PROJECT = "project"
private const val TYPE = "type"
private val copyTypes = setOf("Copy", "Sync")

private fun SourceToken.isCopyType(): Boolean = kind == TokenKind.IDENTIFIER && text in copyTypes

/**
 * Finds the blocks of a build script, in Kotlin or in Groovy, that configure what a Gradle task copies: the blocks of
 * `processResources` and of the `copy` and `sync` of the project, those of `tasks.named("x")`, those of
 * `tasks.register("x")` when the task is a `Copy` or a `Sync`, given as a type argument or as the second argument, and
 * the Groovy `task x(type: Copy)`. A task that is named by anything but a literal is not found. Which files the other
 * tasks copy, such as `Jar`, `Zip` and `ProcessResources` made by `withType`, is not read.
 */
internal class CopyBlocks(script: ScriptTokens) {
    private val calls = QualifiedCalls(script)

    fun all(): List<CopyBlock> = (projectBlocks() + namedBlocks() + registeredBlocks() + declaredBlocks())
        .sortedBy { it.block.interior.first }

    private fun projectBlocks(): List<CopyBlock> = calls.named(setOf(PROCESS_RESOURCES, COPY, SYNC))
        .filter { it.name.text == PROCESS_RESOURCES || it.receiver == null || it.receiver == PROJECT }
        .map { CopyBlock(it.name.text, it.block) }

    private fun namedBlocks(): List<CopyBlock> =
        calls.named(setOf("named")).filter { it.receiver == TASKS }.mapNotNull(::blockOf)

    private fun registeredBlocks(): List<CopyBlock> =
        calls.named(setOf("register")).filter { it.receiver == TASKS && isCopyTyped(it) }.mapNotNull(::blockOf)

    private fun declaredBlocks(): List<CopyBlock> = calls.declaredBy("task")
        .filter { call -> call.arguments.any(::isTypeArgument) }
        .map { CopyBlock(it.name.text, it.block) }

    private fun blockOf(call: QualifiedCall): CopyBlock? =
        call.arguments.firstOrNull()?.singleOrNull()?.let { ScriptLiterals.plain(it.text) }
            ?.let { CopyBlock(it, call.block) }

    private fun isCopyTyped(call: QualifiedCall): Boolean =
        (call.typeArguments + call.arguments.drop(1).flatten()).any { it.isCopyType() }

    private fun isTypeArgument(group: List<SourceToken>): Boolean =
        group.firstOrNull()?.isIdentifier(TYPE) == true && group.any { it.isCopyType() }
}
