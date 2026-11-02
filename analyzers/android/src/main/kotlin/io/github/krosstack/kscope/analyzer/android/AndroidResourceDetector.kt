package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector

/**
 * Where a file lies in an Android `res` directory: `<folder>[-<qualifiers>]/<file>`. This is the only place that
 * reads resource paths; everything else works on artifact types and facts.
 */
internal data class ResourceLocation(
    val type: String,
    val qualifiers: String?,
    val baseName: String,
    val isXml: Boolean,
    val isKnownType: Boolean,
) {
    val isDefaultConfiguration: Boolean get() = qualifiers == null

    /** The folder as written, such as `values-de`. */
    val folder: String get() = if (qualifiers == null) type else "$type-$qualifiers"
}

internal object ResourceLocations {
    private const val RESOURCE_DIRECTORY = "res"
    private const val SOURCE_DIRECTORY = "src"
    private const val DEPTH_BELOW_RES = 2

    private val knownTypes: Set<String> = setOf(
        "values", "layout", "drawable", "mipmap", "menu", "navigation", "raw", "font", "xml", "anim", "animator",
        "color", "interpolator", "transition",
    )

    /**
     * A file below a `res` directory, in a folder of a known type or in the conventional `src/<source set>/res` root.
     * Null for anything else, including hidden files.
     */
    fun of(path: String): ResourceLocation? {
        val segments = path.split('/')
        val resIndex = segments.size - DEPTH_BELOW_RES - 1
        val isBelowResDirectory = resIndex >= 0 && segments[resIndex] == RESOURCE_DIRECTORY
        return if (isBelowResDirectory && !segments.last().startsWith('.')) locationIn(segments, resIndex) else null
    }

    private fun locationIn(segments: List<String>, resIndex: Int): ResourceLocation? {
        val folder = segments[resIndex + 1]
        val fileName = segments.last()
        val type = folder.substringBefore('-')
        val isConventionalRoot = resIndex >= 2 && segments[resIndex - 2] == SOURCE_DIRECTORY
        val isKnown = type in knownTypes
        return ResourceLocation(
            type = type,
            qualifiers = folder.substringAfter('-', "").takeIf { it.isNotEmpty() },
            baseName = fileName.substringBefore('.'),
            isXml = fileName.substringAfterLast('.', "").equals("xml", ignoreCase = true),
            isKnownType = isKnown,
        ).takeIf { isKnown || isConventionalRoot }
    }

    fun artifactTypeOf(location: ResourceLocation): ArtifactType = when {
        location.type == "values" && location.isXml -> AndroidArtifactTypes.values
        location.type == "layout" -> AndroidArtifactTypes.layout
        location.type == "navigation" -> AndroidArtifactTypes.navigation
        location.type == "menu" -> AndroidArtifactTypes.menu
        location.type == "drawable" -> AndroidArtifactTypes.drawable
        else -> AndroidArtifactTypes.other
    }
}

/** Claims the files of the resource folders that [type] stands for. */
internal class ResourceFolderDetector(private val type: ArtifactType) : ArtifactDetector {
    override val id: String = type.id

    override fun detect(candidate: ArtifactCandidate): ArtifactType? {
        val location = ResourceLocations.of(candidate.path.value) ?: return null
        return type.takeIf { ResourceLocations.artifactTypeOf(location) == it }
    }
}
