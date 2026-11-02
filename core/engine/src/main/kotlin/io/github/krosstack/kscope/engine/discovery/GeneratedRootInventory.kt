package io.github.krosstack.kscope.engine.discovery

import io.github.krosstack.kscope.engine.build.DirectoryIndex
import io.github.krosstack.kscope.engine.discovery.roots.RootDirectories
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.OutputRoot

/**
 * Whether the files of generated source roots are handed to producers. They are inventoried either way; analysing
 * them as sources is switched on here once producers can tell generated code from hand-written code. The files of
 * output roots that detectors name are handed over whatever this says: a label that starts with
 * [OutputRoot.LABEL_PREFIX] is how a producer tells them from hand-written files.
 */
internal object GeneratedRootPolicy {
    const val HAND_TO_PRODUCERS = false

    fun isHandedToProducers(artifact: Artifact): Boolean {
        val generated = artifact.generated ?: return true
        return HAND_TO_PRODUCERS || generated.generator.startsWith(OutputRoot.LABEL_PREFIX)
    }
}

/**
 * Adds the files of the generated roots that the build model names. Git ignores build output, so discovery does not
 * find these files; they are read from disk when their root exists. Every artifact below a generated root, found by
 * discovery or here, carries [GeneratedInfo].
 */
internal class GeneratedRootInventory(private val directories: RootDirectories) {
    fun merge(repository: Repository, inventory: Inventory, sourceSets: List<SourceSet>): Inventory {
        val generatedRoots = sourceSets.flatMap { it.roots }.filter { it.generated }.distinctBy { it.directory.value }
        if (generatedRoots.isEmpty()) return inventory
        val known = inventory.artifacts.mapTo(HashSet()) { it.ref.path.value }
        val found = directories.read(repository, generatedRoots.map { it.directory.value }, known)
        val labels = DirectoryIndex(generatedRoots.associate { it.directory.value to generatorOf(it) })
        val artifacts = (inventory.artifacts + found.artifacts).sortedBy { it.ref.path.value }.map { artifact ->
            labels.nearest(artifact.ref.path.value)?.let { artifact.copy(generated = GeneratedInfo(it, null)) }
                ?: artifact
        }
        return Inventory(artifacts, inventory.notices + found.notices, inventory.candidates + found.candidates)
    }

    private fun generatorOf(generatedRoot: SourceRoot): String {
        val segments = generatedRoot.directory.value.split('/')
        val afterGenerated = segments.dropWhile { it != GENERATED_DIRECTORY }.drop(1).firstOrNull()
        return afterGenerated ?: DEFAULT_GENERATOR
    }

    private companion object {
        const val GENERATED_DIRECTORY = "generated"
        const val DEFAULT_GENERATOR = "build-output"
    }
}
