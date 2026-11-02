package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/**
 * Where a file lies below a `composeResources` directory: `<folder>[-<qualifiers>]/<file>`, or `files/<path>`. This is
 * the only place that reads resource paths; everything else works on artifact types and facts.
 */
internal data class ComposeResourceLocation(
    val kind: ResourceFolder,
    val folderName: String,
    val qualifiers: String?,
    val baseName: String,
) {
    val isDefaultConfiguration: Boolean get() = qualifiers == null
}

internal enum class ResourceFolder { VALUES, DRAWABLE, FONT, FILES }

internal object ComposeResourceLocations {
    private const val RESOURCE_ROOT = "composeResources"
    private const val FILES = "files"
    private const val VALUES = "values"
    private const val DRAWABLE = "drawable"
    private const val FONT = "font"
    private const val FLAT_DEPTH = 2

    /** Null for anything that is no resource: other folders, hidden files and files that `values` cannot hold. */
    fun of(path: String): ComposeResourceLocation? {
        val segments = path.split('/')
        val rootIndex = segments.indexOfLast { it == RESOURCE_ROOT }
        val inside = if (rootIndex < 0) emptyList() else segments.drop(rootIndex + 1)
        return if (inside.size < FLAT_DEPTH || inside.last().startsWith('.')) null else locationIn(inside)
    }

    private fun locationIn(inside: List<String>): ComposeResourceLocation? {
        val folder = inside.first()
        val file = inside.last()
        val isXml = file.substringAfterLast('.', "").equals("xml", ignoreCase = true)
        val kind = kindOf(folder, isFlat = inside.size == FLAT_DEPTH, isXml = isXml) ?: return null
        return ComposeResourceLocation(
            kind = kind,
            folderName = folder,
            qualifiers = folder.substringAfter('-', "").takeIf { it.isNotEmpty() },
            baseName = file.substringBeforeLast('.'),
        )
    }

    private fun kindOf(folder: String, isFlat: Boolean, isXml: Boolean): ResourceFolder? = when {
        folder == FILES -> ResourceFolder.FILES
        !isFlat -> null
        folder.substringBefore('-') == VALUES -> ResourceFolder.VALUES.takeIf { isXml }
        folder.substringBefore('-') == DRAWABLE -> ResourceFolder.DRAWABLE
        folder.substringBefore('-') == FONT -> ResourceFolder.FONT
        else -> null
    }

    fun artifactTypeOf(location: ComposeResourceLocation): ArtifactType = when (location.kind) {
        ResourceFolder.VALUES -> ComposeArtifactTypes.values
        ResourceFolder.DRAWABLE -> ComposeArtifactTypes.drawable
        ResourceFolder.FONT -> ComposeArtifactTypes.font
        ResourceFolder.FILES -> ComposeArtifactTypes.file
    }
}

/** Claims the files of the resource folders that [type] stands for. */
internal class ComposeResourceFolderDetector(private val type: ArtifactType) : ArtifactDetector {
    override val id: String = type.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val location = ComposeResourceLocations.of(candidate.path.value) ?: return null
        return type.takeIf { ComposeResourceLocations.artifactTypeOf(location) == it }
    }
}
