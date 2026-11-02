package io.github.krosstack.kscope.analyzer.android.rclass

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.io.StringReader
import java.util.Properties

private const val NON_TRANSITIVE_PROPERTY = "android.nonTransitiveRClass"

/** The Gradle properties of each build root decide whether `R` holds the resources of dependencies as well. */
internal class RClassOptions(
    private val files: WorkspaceFiles,
    artifacts: List<Artifact>,
    private val buildRoots: List<BuildRoot>,
) {
    private val propertyFilesByDirectory: Map<String, Artifact> = artifacts
        .filter { it.type == AndroidArtifactTypes.gradleProperties }
        .associateBy { it.ref.path.value.substringBeforeLast('/', "") }

    /** True unless the Gradle properties of the build root say `android.nonTransitiveRClass=false`. */
    fun isNonTransitive(buildRoot: BuildRootId): Boolean {
        val directory = buildRoots.firstOrNull { it.id == buildRoot }?.directory?.value.orEmpty()
        val value = propertyFilesByDirectory[directory]?.let(::propertiesOf)?.getProperty(NON_TRANSITIVE_PROPERTY)
        return value?.trim()?.equals("false", ignoreCase = true) != true
    }

    private fun propertiesOf(artifact: Artifact): Properties = try {
        Properties().apply { load(StringReader(files.read(artifact.ref).toString(Charsets.ISO_8859_1))) }
    } catch (_: IOException) {
        Properties()
    }
}

/** The source set whose stub declares the `R` class of a module: its `main`, else the first main-like source set. */
internal object StubHost {
    fun of(moduleSourceSets: List<SourceSet>, targets: List<Target>): SourceSet? {
        val mains = moduleSourceSets.filter { it.role == SourceSetRole.MAIN }.sortedBy { it.id.value }
        val androidTargets = targets.filter { it.platform == Platform.ANDROID }.map { it.id }.toSet()
        return mains.firstOrNull { it.name == "main" }
            ?: mains.firstOrNull { sourceSet -> sourceSet.targets.any { it in androidTargets } }
            ?: mains.firstOrNull()
    }
}
