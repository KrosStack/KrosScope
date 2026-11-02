package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.script.blocks
import io.github.krosstack.kscope.analyzer.gradle.script.namedArgument
import io.github.krosstack.kscope.analyzer.gradle.script.positional
import io.github.krosstack.kscope.analyzer.gradle.script.setting
import io.github.krosstack.kscope.analyzer.gradle.script.unquote
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.spi.worker.ScriptCall

private val mavenPublicationType = Regex("""(?:[\w.]*\.)?MavenPublication(?:::class(?:\.java)?|\.class)?""")
private val coordinateNames = listOf("groupId", "artifactId", "version")

/**
 * The publications a build declares: the `MavenPublication` objects of `publishing { publications { } }`, at the top
 * of a script or inside `afterEvaluate { }`, and the `coordinates(...)` call of `mavenPublishing { }`.
 */
internal object PublicationDeclarations {
    fun read(sources: List<ScriptSource>): List<Declared> = sources.flatMap { source ->
        mavenPublications(source) + coordinates(source)
    }

    private fun publishingBlocks(calls: List<ScriptCall>): List<ScriptCall> =
        calls.blocks("publishing") + ProjectStatements.afterEvaluateBodies(calls).flatMap(::publishingBlocks)

    private fun mavenPublications(source: ScriptSource): List<Declared> = publishingBlocks(source.calls)
        .flatMap { block -> block.body.filter { it.name == "publications" && it.receiver == null } }
        .flatMap { it.body }
        .filter(::isMavenPublication)
        .map { call ->
            val (group, artifact, version) = coordinateNames.map { settingPart(call, it, source.heuristic) }
            Declared(group, artifact, version)
        }

    private fun coordinates(source: ScriptSource): List<Declared> = source.calls.blocks("mavenPublishing")
        .flatMap { block -> block.body.filter { it.name == "coordinates" && it.receiver == null } }
        .map { call ->
            val positional = call.positional()
            val (group, artifact, version) = coordinateNames.mapIndexed { index, name ->
                (call.namedArgument(name) ?: positional.getOrNull(index))
                    ?.let { Part(it.literal, it.expression, source.heuristic) }
            }
            Declared(group, artifact, version)
        }

    private fun isMavenPublication(call: ScriptCall): Boolean =
        call.typeArguments.any { mavenPublicationType.matches(it.trim()) } ||
            call.arguments.any { mavenPublicationType.matches(it.expression.trim()) }

    private fun settingPart(call: ScriptCall, name: String, heuristic: Boolean): Part? =
        call.setting(name)?.let { Part(unquote(it), it, heuristic) }
}
