package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.engine.build.archive.JvmLibraryLinks
import io.github.krosstack.kscope.engine.build.archive.JvmLibraryResolution
import io.github.krosstack.kscope.engine.build.archive.folders.ArtifactFolders
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.RepositoryCheckout
import java.nio.file.Path

internal class ModuleSpec(
    val publications: String? = null,
    val traits: Set<ModuleTrait> = emptySet(),
    val targetNames: List<String> = emptyList(),
    val heuristic: Boolean = false,
    val archives: String? = null,
)

/** Repositories, modules and dependencies built by hand, as the merged build model of a workspace. */
internal class WorkspaceModelDraft {
    private val repositories = mutableListOf<Repository>()
    private val roots = mutableListOf<BuildRoot>()
    private val modules = mutableListOf<Module>()
    private val targets = mutableListOf<Target>()
    private val sourceSets = mutableListOf<SourceSet>()
    private val artifacts = mutableListOf<Artifact>()
    private val dependencies = mutableListOf<Relationship>()

    fun repository(directory: String) {
        repositories += Repository(RepositoryId(directory), directory, null, null, RepoPath(directory))
        roots += BuildRoot(BuildRootId("$directory//"), RepositoryId(directory), RepoPath(""), BuildSystem.GRADLE)
    }

    fun module(repository: String, path: String, spec: ModuleSpec = ModuleSpec()): ModuleId {
        val id = ModuleId("$repository//$path")
        val attributes = listOfNotNull(
            spec.publications?.let { "maven.publications" to it },
            spec.archives?.let { "jvm.archives" to it },
        ).toMap()
        val certainty = if (spec.heuristic) Certainty.HEURISTIC else Certainty.EXACT
        modules += Module(
            id = id,
            buildRoot = BuildRootId("$repository//"),
            path = path,
            directory = RepoPath(path.removePrefix(":")),
            plugins = emptyList(),
            traits = spec.traits,
            androidNamespace = null,
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = certainty,
            attributes = attributes,
        )
        targets += spec.targetNames.map { Target(TargetId("$repository//$path#target/$it"), id, it, Platform.JVM) }
        return id
    }

    fun dependsOn(consumer: ModuleId, coordinate: String, version: String?, line: Int = 3) {
        val attributes = listOfNotNull("configuration" to "implementation", version?.let { "version" to it }).toMap()
        dependency(consumer, "maven:$coordinate", Certainty.EXACT, attributes, line)
    }

    fun dependsOnFile(
        consumer: ModuleId,
        path: String,
        certainty: Certainty = Certainty.EXACT,
        line: Int = 3,
        attributes: Map<String, String> = emptyMap(),
    ) {
        dependency(consumer, "file:$path", certainty, mapOf("configuration" to "implementation") + attributes, line)
    }

    fun sources(module: ModuleId, role: SourceSetRole, root: String, files: List<String>) {
        val id = SourceSetId("${module.value}@${role.name.lowercase()}")
        val roots = listOf(SourceRoot(RepoPath(root), SourceRootKind("kotlin"), false))
        sourceSets += SourceSet(
            id, module, role.name.lowercase(), role, emptyList(), roots, emptyList(), emptyList(),
            EvidenceClass.BUILD_MODEL, Certainty.EXACT,
        )
        val repository = RepositoryId(module.value.substringBefore("//"))
        artifacts += files.map { file ->
            Artifact(ArtifactRef(repository, RepoPath("$root/$file")), typeOf(file), ContentHash(file), 1, 1, id, null)
        }
    }

    private fun typeOf(file: String): ArtifactType {
        val id = if (file.endsWith(".java")) "java.source" else "kotlin.source"
        return ArtifactType(id, ArtifactCategory.SOURCE)
    }

    private fun dependency(
        consumer: ModuleId,
        target: String,
        certainty: Certainty,
        attributes: Map<String, String>,
        line: Int,
    ) {
        val repository = RepositoryId(consumer.value.substringBefore("//"))
        val artifact = ArtifactRef(repository, RepoPath("gradle/libs.versions.toml"))
        dependencies += Relationship(
            from = NodeRef.OfModule(consumer),
            kind = RelationKind.DEPENDS_ON,
            subKind = null,
            resolution = Resolution.Resolved(NodeRef.OfExternal(target), certainty),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            site = SourceLocation(artifact, TextRange(line, 1, line, 9)),
            scope = Scope(),
            attributes = attributes,
        )
    }

    fun resolve(): CoordinateLinks = CoordinateResolution(repositories.toList(), model()).resolve()

    fun resolveJvmLibraries(base: Path, folders: List<Path> = emptyList()): JvmLibraryLinks {
        val checkouts = repositories.map { RepositoryCheckout(it, base.resolve(it.directory.value)) }
        return JvmLibraryResolution(checkouts, model(), artifacts.toList(), ArtifactFolders(folders)).resolve()
    }

    fun model() = BuildModel(
        buildRoots = roots.toList(),
        modules = modules.toList(),
        targets = targets.toList(),
        variants = emptyList(),
        sourceSets = sourceSets.toList(),
        dependencies = dependencies.toList(),
    )
}
