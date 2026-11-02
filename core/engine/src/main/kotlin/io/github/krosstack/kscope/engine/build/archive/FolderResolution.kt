package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.PublicationIndex
import io.github.krosstack.kscope.engine.build.archive.folders.ArtifactFolders
import io.github.krosstack.kscope.engine.build.archive.folders.FoundArchive
import io.github.krosstack.kscope.engine.build.archive.folders.MavenCoordinate
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.spi.BuildModel

/** A link that was made for a coordinate, and the archive of a registered folder that was read for it, if any. */
internal class FolderOutcome(val relationship: Relationship, val found: FoundArchive?)

/**
 * Resolves the dependencies on a Maven coordinate with a literal version to the module of another repository that
 * builds the JAR or AAR of that name, through the archive that a registered folder holds for it, which is compared
 * with the module as a JAR on a file path is. A coordinate that a module of another repository publishes is left to
 * the coordinate links. A producer whose archive no folder holds is `artifact.output-missing`, and a name that several
 * modules build is `artifact.ambiguous-producer`. An archive that no module builds stays an external dependency.
 */
internal class FolderResolution(
    private val folders: ArtifactFolders,
    private val model: BuildModel,
    private val index: ArchiveIndex,
    private val content: ArchiveContent,
    private val ownership: ModuleOwnership,
) {
    private val published = PublicationIndex(model.modules, model.targets)

    fun resolve(): List<FolderOutcome> =
        if (folders.isEmpty) emptyList() else model.dependencies.mapNotNull(::outcomeOf)

    private fun outcomeOf(edge: Relationship): FolderOutcome? {
        val coordinate = coordinateOf(edge)
        val consumer = ownership.moduleOf(edge.from)
        return if (coordinate == null || consumer == null) null else outcomeFor(edge, consumer, coordinate)
    }

    private fun outcomeFor(edge: Relationship, consumer: Module, coordinate: MavenCoordinate): FolderOutcome? {
        val repository = ownership.repositoryOf(consumer)
        val archive = DeclaredArchive(edge, consumer, repository, coordinate.jarName)
        val producers = producersOf(coordinate, repository)
        return when {
            isPublishedElsewhere(coordinate, repository) || producers.isEmpty() -> null
            producers.size > 1 -> FolderOutcome(JvmLibraryEdges.ambiguous(archive), null)
            else -> linked(archive, coordinate, producers.single())
        }
    }

    private fun linked(archive: DeclaredArchive, coordinate: MavenCoordinate, producer: Module): FolderOutcome {
        val found = folders.find(coordinate) ?: return FolderOutcome(JvmLibraryEdges.outputMissing(archive), null)
        val read = DeclaredArchive(archive.edge, archive.consumer, archive.repository, found.fileName, found.path)
        return FolderOutcome(JvmLibraryEdges.link(read, producer, content.stateOf(read, producer)), found)
    }

    private fun coordinateOf(edge: Relationship): MavenCoordinate? {
        val target = (edge.resolution as? Resolution.Resolved)?.target as? NodeRef.OfExternal
        val named = target?.coordinate?.takeIf { edge.kind == RelationKind.DEPENDS_ON && it.startsWith(MAVEN) }
        val parts = named?.removePrefix(MAVEN)?.split(':').orEmpty()
        val version = edge.attributes["version"] ?: parts.getOrNull(VERSION_PART)
        return if (parts.size >= 2) MavenCoordinate.of(parts[0], parts[1], version) else null
    }

    private fun isPublishedElsewhere(coordinate: MavenCoordinate, repository: RepositoryId?): Boolean =
        published.find(coordinate.group, coordinate.artifact).any { ownership.repositoryOf(it.module) != repository }

    private fun producersOf(coordinate: MavenCoordinate, repository: RepositoryId?): List<Module> =
        listOf(coordinate.jarName, coordinate.aarName).flatMap(index::producersOf).distinct()
            .filter { ownership.repositoryOf(it) != repository }

    private companion object {
        const val MAVEN = "maven:"
        const val VERSION_PART = 2
    }
}
