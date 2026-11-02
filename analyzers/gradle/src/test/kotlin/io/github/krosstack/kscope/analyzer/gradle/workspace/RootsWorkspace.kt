package io.github.krosstack.kscope.analyzer.gradle.workspace

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.spi.BuildModel
import java.nio.file.Files
import java.nio.file.Path

/** A temporary workspace directory and the build model entities that describe projects in it. */
internal class RootsWorkspace : AutoCloseable {
    private val root: Path = Files.createTempDirectory("kscope-roots")
    private val buildRoot = BuildRoot(BuildRootId("r//"), RepositoryId("r"), RepoPath(""), BuildSystem.GRADLE)

    fun file(path: String) {
        val target = root.resolve(path)
        Files.createDirectories(target.parent)
        Files.writeString(target, "x")
    }

    fun module(path: String, directory: String) = Module(
        id = ModuleId("r//$path"),
        buildRoot = buildRoot.id,
        path = path,
        directory = RepoPath(directory),
        plugins = emptyList(),
        traits = emptySet(),
        androidNamespace = null,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    fun sourceSet(module: Module, name: String, roots: List<SourceRoot>) = SourceSet(
        id = SourceSetId("${module.id.value}@$name"),
        module = module.id,
        name = name,
        role = SourceSetRole.MAIN,
        dependsOn = emptyList(),
        roots = roots,
        targets = emptyList(),
        variants = emptyList(),
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    fun model(modules: List<Module>, sourceSets: List<SourceSet>, targets: List<Target> = emptyList()) =
        BuildModel(listOf(buildRoot), modules, targets, emptyList(), sourceSets, emptyList())

    fun supplement(model: BuildModel): BuildModel = ConventionRoots(WorkspaceDisk(root)).supplement(model)

    override fun close() {
        root.toFile().deleteRecursively()
    }
}
