package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ProjectDocument
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind

private const val PRODUCTION_COMPILATION = "main"

/** A source set as the Gradle model describes it, before it is given an identity. */
internal class SourceSetDraft(val name: String, val roots: List<SourceRoot>, val dependsOn: List<String>)

/**
 * Gathers the source sets of one project from the Kotlin, Android and Java sections, merges those of the same name
 * and keeps the ones that have a directory on disk, together with every source set they depend on. The production
 * source set of a target is kept as well when a source set it depends on has a directory: one fed only by generated
 * code has no directory before the first build, and keeping it makes a fresh checkout read the same as a built one.
 */
internal class SourceSetDrafts(private val document: ProjectDocument, private val workspace: WorkspacePaths) {
    fun emitted(): List<SourceSetDraft> {
        val drafts = merged()
        val byName = drafts.associateBy { it.name }
        val withDirectory = drafts.filter { it.roots.isNotEmpty() }.map { it.name }.toSet()
        val kept = (withDirectory + productionSourceSets().filter { dependsOnAny(it, withDirectory, byName) })
            .toMutableSet()
        val pending = ArrayDeque(kept)
        while (pending.isNotEmpty()) {
            byName[pending.removeFirst()]?.dependsOn.orEmpty().filter(kept::add).forEach(pending::addLast)
        }
        return drafts.filter { it.name in kept }.sortedBy { it.name }
    }

    private fun productionSourceSets(): List<String> = document.kotlin?.targets.orEmpty()
        .flatMap { it.compilations }
        .filter { it.name == PRODUCTION_COMPILATION }
        .mapNotNull { it.defaultSourceSet }

    private fun dependsOnAny(name: String, wanted: Set<String>, byName: Map<String, SourceSetDraft>): Boolean {
        val seen = mutableSetOf(name)
        val pending = ArrayDeque(listOf(name))
        while (pending.isNotEmpty()) {
            val next = byName[pending.removeFirst()]?.dependsOn.orEmpty().filter(seen::add)
            if (next.any { it in wanted }) return true
            pending.addAll(next)
        }
        return false
    }

    private fun merged(): List<SourceSetDraft> =
        (fromKotlin() + fromAndroid() + fromJava()).groupBy { it.name }.map { (name, parts) ->
            val roots = parts.flatMap { it.roots }.distinct().sortedBy { it.directory.value + "|" + it.kind.value }
            SourceSetDraft(name, roots, parts.flatMap { it.dependsOn }.distinct().sorted())
        }

    private fun fromKotlin(): List<SourceSetDraft> = document.kotlin?.sourceSets.orEmpty().map { sourceSet ->
        val roots = sourceSet.kotlinDirs.mapNotNull { root(it, "kotlin") } +
            sourceSet.resourceDirs.mapNotNull { root(it, "resources") }
        SourceSetDraft(sourceSet.name, roots, sourceSet.dependsOn)
    }

    private fun fromAndroid(): List<SourceSetDraft> = document.android?.sourceSets.orEmpty().map { sourceSet ->
        val directories = sourceSet.dirs.flatMap { (kind, dirs) -> dirs.mapNotNull { root(it, androidKind(kind)) } }
        val manifest = sourceSet.manifest?.let { root(it, "android.manifest", exact = true) }
        SourceSetDraft(sourceSet.name, directories + listOfNotNull(manifest), emptyList())
    }

    private fun fromJava(): List<SourceSetDraft> = document.java.orEmpty().map { sourceSet ->
        val roots = sourceSet.javaDirs.mapNotNull { root(it, "java") } +
            sourceSet.resourceDirs.mapNotNull { root(it, "resources") }
        SourceSetDraft(sourceSet.name, roots, emptyList())
    }

    private fun androidKind(directoryKind: String) = when (directoryKind) {
        "res" -> "android.res"
        "assets" -> "android.assets"
        "jniLibs" -> "android.jniLibs"
        else -> directoryKind
    }

    private fun root(absolute: String, fallbackKind: String, exact: Boolean = false): SourceRoot? {
        val directory = workspace.relative(absolute) ?: return null
        val kind = if (exact) SourceRootKind(fallbackKind) else SourceSetNaming.rootKindOf(absolute, fallbackKind)
        return SourceRoot(directory, kind, generated = isGenerated(absolute))
    }

    private fun isGenerated(absolute: String): Boolean =
        absolute == document.buildDir || absolute.startsWith(document.buildDir + "/")
}
