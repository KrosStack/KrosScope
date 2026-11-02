package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.engine.detector.CoreArtifactTypes
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult

/**
 * Infers build roots, modules and source sets from the layout of a Gradle workspace without running any build logic.
 * It works on artifact types and paths only, and labels everything it derives as heuristic.
 */
public class StructuralBuildModelProvider : BuildModelProvider {
    override val producer: ProducerRef = ProducerRef("kscope.build.structural", "1")

    override val requiresTrust: Boolean = false

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val roots = buildRoots(request.repository.id, request.artifacts)
        if (roots.isEmpty()) return BuildModelResult.NotApplicable
        val rootIndex = DirectoryIndex(roots.associateBy { it.directory.value })
        val scripts = directoriesOf(request.artifacts, CoreArtifactTypes.GRADLE_BUILD_SCRIPT)
        val modules = scripts.mapNotNull { directory -> moduleAt(directory, rootIndex) }
        val orphans = scripts.filter { rootIndex.nearest(it, includeSelf = true) == null }
        val sourceSets = SourceSetInference(modules).infer(request.artifacts)
        val model = BuildModel(roots, modules, emptyList(), emptyList(), sourceSets, emptyList())
        return BuildModelResult.Provided(model, orphans.map(::orphanNotice))
    }

    private fun buildRoots(repository: RepositoryId, artifacts: List<Artifact>): List<BuildRoot> =
        directoriesOf(artifacts, CoreArtifactTypes.GRADLE_SETTINGS).map { directory ->
            val id = BuildRootId("${repository.value}//$directory")
            BuildRoot(id, repository, RepoPath(directory), BuildSystem.GRADLE)
        }

    private fun directoriesOf(artifacts: List<Artifact>, typeId: String): List<String> =
        artifacts.filter { it.type.id == typeId }.map { parentOf(it.ref.path.value) }.distinct().sorted()

    private fun moduleAt(directory: String, roots: DirectoryIndex<BuildRoot>): Module? {
        val root = roots.nearest(directory, includeSelf = true) ?: return null
        val relative = directory.removePrefix(root.directory.value).trim('/')
        val gradlePath = if (relative.isEmpty()) ROOT_PATH else ROOT_PATH + relative.replace('/', ':')
        return Module(
            id = ModuleId(root.id.value + gradlePath),
            buildRoot = root.id,
            path = gradlePath,
            directory = RepoPath(directory),
            plugins = emptyList(),
            traits = emptySet(),
            androidNamespace = null,
            evidenceClass = EvidenceClass.STRUCTURAL,
            certainty = Certainty.HEURISTIC,
        )
    }

    private fun orphanNotice(directory: String) = Diagnostic(
        severity = DiagnosticSeverity.INFO,
        code = ReasonCode("build.structural.script-outside-build-root"),
        message = "The build script in '$directory' has no settings script above it and is not modelled.",
        location = null,
    )

    private companion object {
        const val ROOT_PATH = ":"
    }
}
