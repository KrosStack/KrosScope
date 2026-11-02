package io.github.krosstack.kscope.analyzer.gradle.statics.plugin

import io.github.krosstack.kscope.analyzer.gradle.mapping.ComposeResourceAttributes
import io.github.krosstack.kscope.analyzer.gradle.script.blocks
import io.github.krosstack.kscope.analyzer.gradle.script.firstLiteral
import io.github.krosstack.kscope.analyzer.gradle.script.flagSetting
import io.github.krosstack.kscope.analyzer.gradle.script.stringSetting
import io.github.krosstack.kscope.analyzer.gradle.statics.ScriptSource
import io.github.krosstack.kscope.spi.worker.ScriptCall

private const val ROOT_PATH = ":"

/**
 * Reads what a module that applies the Compose Multiplatform plugin says about its resource class: the
 * `compose.resources { packageOfResClass = ...; publicResClass = true }` block and the Gradle group. A group that no
 * script sets is the one Gradle gives a project: the name of the root project followed by the parents of the project.
 */
internal object ComposeResourceReader {
    fun attributes(ids: Set<String>, sources: List<ScriptSource>, project: ProjectGroup): Map<String, String> {
        if (ComposeResourceAttributes.PLUGIN_ID !in ids) return emptyMap()
        val calls = sources.flatMap { it.calls }
        val blocks = resourceBlocks(calls)
        return ComposeResourceAttributes.of(
            packageOfResClass = blocks.firstNotNullOfOrNull { it.stringSetting("packageOfResClass") },
            isPublic = blocks.firstNotNullOfOrNull { it.flagSetting("publicResClass") },
            group = explicitGroup(calls) ?: project.defaultGroup(),
        )
    }

    private fun resourceBlocks(calls: List<ScriptCall>): List<ScriptCall> =
        calls.filter { it.name == "resources" && it.receiver == "compose" } +
            calls.blocks("compose").flatMap { block -> block.body.filter { it.name == "resources" } }

    private fun explicitGroup(calls: List<ScriptCall>): String? =
        calls.firstOrNull { it.name == "=" && it.receiver == "group" }?.firstLiteral()
            ?: calls.blocks("group").firstNotNullOfOrNull { it.firstLiteral() }
}

/** Where a project lies in its build: the group Gradle gives it follows from the root name and its path. */
internal class ProjectGroup(private val path: String, private val rootName: String?) {
    fun defaultGroup(): String? {
        val parents = path.removePrefix(ROOT_PATH).split(':').filter { it.isNotEmpty() }.dropLast(1)
        return if (path == ROOT_PATH || rootName == null) null else (listOf(rootName) + parents).joinToString(".")
    }
}
