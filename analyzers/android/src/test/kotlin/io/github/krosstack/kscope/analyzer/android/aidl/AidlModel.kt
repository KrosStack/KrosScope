package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.spi.BuildModel

/** The repository of the AIDL tests: a library `:svc` that declares contracts and an application `:app`. */
internal object AidlModel {
    val repository = RepositoryId("repo")
    private val buildRoot = BuildRootId("repo//")

    fun moduleId(path: String) = ModuleId("repo//$path")

    fun sourceSetId(path: String, name: String) = SourceSetId("repo//$path@$name")

    private fun module(path: String, namespace: String, attributes: Map<String, String>) = Module(
        id = moduleId(path),
        buildRoot = buildRoot,
        path = path,
        directory = RepoPath(path.removePrefix(":")),
        plugins = emptyList(),
        traits = setOf(ModuleTrait.ANDROID_LIBRARY),
        androidNamespace = namespace,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
        attributes = attributes,
    )

    private fun sourceSet(path: String, name: String): SourceSet {
        val directory = path.removePrefix(":")
        return SourceSet(
            id = sourceSetId(path, name),
            module = moduleId(path),
            name = name,
            role = SourceSetRole.MAIN,
            dependsOn = emptyList(),
            roots = listOf("aidl", "kotlin", "java").map {
                SourceRoot(RepoPath("$directory/src/$name/$it"), SourceRootKind(it), false)
            },
            targets = listOf(TargetId("repo//$path#target/android")),
            variants = emptyList(),
            evidenceClass = EvidenceClass.BUILD_MODEL,
            certainty = Certainty.EXACT,
        )
    }

    fun standard(svcAttributes: Map<String, String> = emptyMap()): BuildModel = BuildModel(
        buildRoots = listOf(BuildRoot(buildRoot, repository, RepoPath(""), BuildSystem.GRADLE)),
        modules = listOf(
            module(":svc", "sample.aidl.svc", svcAttributes),
            module(":app", "sample.aidl.app", emptyMap()),
        ),
        targets = listOf(":svc", ":app").map {
            Target(TargetId("repo//$it#target/android"), moduleId(it), "android", Platform.ANDROID)
        },
        variants = emptyList(),
        sourceSets = listOf(sourceSet(":svc", "main"), sourceSet(":app", "main")),
        dependencies = emptyList(),
    )
}
