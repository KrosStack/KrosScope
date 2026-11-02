package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.engine.build.archive.folders.ArtifactFolders
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.RepositoryCheckout

/**
 * Resolves the dependencies on JAR files that a module declares to the modules of other repositories that build a
 * JAR of that name. A name is never matched inside the consumer's own repository, and a name that several modules
 * build is reported as ambiguous and not linked. A JAR that no module builds stays an external file. The dependencies
 * on a coordinate are resolved through the registered [folders] by [FolderResolution].
 */
internal class JvmLibraryResolution(
    checkouts: List<RepositoryCheckout>,
    private val model: BuildModel,
    artifacts: List<Artifact>,
    private val folders: ArtifactFolders = ArtifactFolders(emptyList()),
) {
    private val ownership = ModuleOwnership(model)
    private val roots = CheckoutRoots(checkouts)
    private val declarations = ArchiveDeclarations(roots)
    private val index = ArchiveIndex(model.modules)
    private val content = ArchiveContent(roots, ProducerStems(model.sourceSets, artifacts), ownership::repositoryOf)
    private val folderResolution = FolderResolution(folders, model, index, content, ownership)

    fun resolve(): JvmLibraryLinks {
        val declared = model.dependencies.flatMap(::declaredBy).mapNotNull(::relationshipOf)
        val fromFolders = folderResolution.resolve()
        val relationships = declared + fromFolders.map { it.relationship }
        val inputs = if (folders.isEmpty) {
            emptyList()
        } else {
            (folders.fingerprint() + fromFolders.mapNotNull { it.found?.fingerprint }).distinct().sorted()
        }
        return JvmLibraryLinks(relationships.distinct().sortedBy(::encoded), inputs)
    }

    private fun declaredBy(edge: Relationship): List<DeclaredArchive> {
        val consumer = ownership.moduleOf(edge.from) ?: return emptyList()
        return declarations.of(edge, consumer, ownership.repositoryOf(consumer))
    }

    private fun relationshipOf(archive: DeclaredArchive): Relationship? {
        val producers = index.producersOf(archive.fileName).filter { ownership.repositoryOf(it) != archive.repository }
        return when {
            producers.isEmpty() -> null
            producers.size > 1 -> JvmLibraryEdges.ambiguous(archive)
            else -> JvmLibraryEdges.link(archive, producers.single(), content.stateOf(archive, producers.single()))
        }
    }

    private fun encoded(relationship: Relationship): String =
        KscopeJson.encodeToString(Relationship.serializer(), relationship)
}
