package io.github.krosstack.kscope.analyzer.gradle.workspace

import io.github.krosstack.kscope.analyzer.gradle.mapping.SourceSetNaming
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.spi.BuildModel

private const val MANIFEST_FILE = "AndroidManifest.xml"
private const val MANIFEST_KIND = "android.manifest"
private const val MAIN = "main"

/**
 * Adds the source roots that exist on disk by convention and that the build model does not expose: the directories
 * of SQLDelight, Compose resources, Protocol Buffers and native code, and any Kotlin, Java, resource, AIDL or manifest
 * root of a kind the source set has no root of in the model, since a build that configures a kind of directory
 * replaces the conventional one. A module whose only sources are in such a directory of `src/main` gets a `main`
 * source set.
 */
internal class ConventionRoots(private val disk: WorkspaceDisk) {
    fun supplement(model: BuildModel): BuildModel {
        val modules = model.modules.associateBy { it.id }
        val supplemented = model.sourceSets.map { sourceSet ->
            val directory = modules[sourceSet.module]?.directory?.value ?: return@map sourceSet
            sourceSet.copy(roots = merge(sourceSet.roots, conventionalRoots(directory, sourceSet.name)))
        }
        val modulesWithSets = model.sourceSets.map { it.module }.toSet()
        val created = model.modules.filter { it.id !in modulesWithSets }.mapNotNull { module ->
            createMain(module, model.targets.filter { it.module == module.id })
        }
        return model.copy(sourceSets = supplemented + created)
    }

    private fun createMain(module: Module, targets: List<Target>): SourceSet? {
        val roots = conventionalRoots(module.directory.value, MAIN)
        return roots.takeIf { it.isNotEmpty() }?.let {
            SourceSet(
                id = SourceSetId("${module.id.value}@$MAIN"),
                module = module.id,
                name = MAIN,
                role = SourceSetRole.MAIN,
                dependsOn = emptyList(),
                roots = merge(emptyList(), it),
                targets = targets.singleOrNull()?.let { target -> listOf(target.id) }.orEmpty(),
                variants = emptyList(),
                evidenceClass = EvidenceClass.CONVENTION,
                certainty = Certainty.HEURISTIC,
            )
        }
    }

    private fun conventionalRoots(moduleDirectory: String, sourceSet: String): List<SourceRoot> {
        val directory = RepoPaths.join(RepoPaths.join(moduleDirectory, "src"), sourceSet)
        return disk.children(directory).mapNotNull { name -> rootOf(RepoPaths.join(directory, name), name) }
    }

    private fun rootOf(path: String, name: String): SourceRoot? {
        val kind = SourceSetNaming.conventionKind(name)
        val rootKind = when {
            kind != null && disk.isDirectory(path) -> kind
            name == MANIFEST_FILE && disk.isFile(path) -> MANIFEST_KIND
            else -> null
        }
        return rootKind?.let { SourceRoot(RepoPath(path), SourceRootKind(it), generated = false) }
    }

    private fun merge(existing: List<SourceRoot>, added: List<SourceRoot>): List<SourceRoot> {
        val known = existing.map { it.directory.value }.toSet()
        val configuredKinds = existing.filterNot { it.generated }.map { it.kind }.toSet()
        return (existing + added.filter { it.directory.value !in known && it.kind !in configuredKinds })
            .sortedBy { it.directory.value + "|" + it.kind.value }
    }
}
