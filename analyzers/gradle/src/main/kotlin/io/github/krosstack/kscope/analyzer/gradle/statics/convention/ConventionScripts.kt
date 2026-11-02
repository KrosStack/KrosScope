package io.github.krosstack.kscope.analyzer.gradle.statics.convention

import io.github.krosstack.kscope.analyzer.gradle.workspace.RepoPaths
import io.github.krosstack.kscope.model.Artifact

private val precompiledScript = Regex("""(.*)/src/main/kotlin/(?:.*/)?([^/]+)\.gradle\.kts""")

/**
 * The precompiled script plugins of the included builds: a script `<id>.gradle.kts` below `src/main/kotlin` of an
 * included build or `buildSrc` is the plugin `<id>`.
 */
internal class ConventionScripts(private val byId: Map<String, Artifact>) {
    fun artifactFor(id: String): Artifact? = byId[id]

    fun all(): Collection<Artifact> = byId.values

    companion object {
        fun of(kotlinScripts: List<Artifact>, includedDirectories: Set<String>): ConventionScripts {
            val byId = kotlinScripts.mapNotNull { script ->
                val match = precompiledScript.matchEntire(script.ref.path.value)
                match?.takeIf { m -> includedDirectories.any { RepoPaths.isWithin(m.groupValues[1], it) } }
                    ?.let { it.groupValues[2] to script }
            }.toMap()
            return ConventionScripts(byId)
        }
    }
}
