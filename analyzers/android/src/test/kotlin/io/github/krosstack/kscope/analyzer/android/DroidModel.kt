package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceRoot
import io.github.krosstack.kscope.model.SourceRootKind
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Target
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.Variant
import io.github.krosstack.kscope.model.VariantId
import io.github.krosstack.kscope.spi.BuildModel

/** The repository of the sample trees: an `:app` that depends on `:lib` and `:util`, with a debug variant. */
internal object DroidModel {
    val repository = RepositoryId("repo")
    private val buildRoot = BuildRootId("repo//")

    fun moduleId(path: String) = ModuleId("repo//$path")

    fun sourceSetId(path: String, name: String) = SourceSetId("repo//$path@$name")

    private fun module(path: String, namespace: String?, attributes: Map<String, String> = emptyMap()) = Module(
        id = moduleId(path),
        buildRoot = buildRoot,
        path = path,
        directory = RepoPath(path.removePrefix(":")),
        plugins = emptyList(),
        traits = setOf(if (path == ":app") ModuleTrait.ANDROID_APPLICATION else ModuleTrait.ANDROID_LIBRARY),
        androidNamespace = namespace,
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
        attributes = attributes,
    )

    private fun variant(path: String, name: String, buildType: String, flavors: List<String> = emptyList()) =
        Variant(VariantId("repo//$path#variant/$name"), moduleId(path), name, buildType, flavors)

    private fun appVariants(flavors: List<String>): List<Variant> {
        if (flavors.isEmpty()) return listOf(variant(":app", "debug", "debug"), variant(":app", "release", "release"))
        return flavors.flatMap { flavor ->
            listOf("debug", "release").map { type ->
                variant(":app", flavor + type.replaceFirstChar(Char::uppercaseChar), type, listOf(flavor))
            }
        }
    }

    private fun sourceSet(
        path: String,
        name: String,
        variants: List<Variant>,
        role: SourceSetRole = SourceSetRole.MAIN,
    ) = SourceSet(
        id = sourceSetId(path, name),
        module = moduleId(path),
        name = name,
        role = role,
        dependsOn = emptyList(),
        roots = listOf(
            SourceRoot(RepoPath("${path.removePrefix(":")}/src/$name/res"), SourceRootKind("android.res"), false),
            SourceRoot(
                RepoPath("${path.removePrefix(":")}/src/$name/AndroidManifest.xml"),
                SourceRootKind("android.manifest"),
                false,
            ),
        ),
        targets = listOf(TargetId("repo//$path#target/android")),
        variants = variants.map { it.id },
        evidenceClass = EvidenceClass.BUILD_MODEL,
        certainty = Certainty.EXACT,
    )

    private fun dependency(from: String, to: String) = Relationship(
        from = NodeRef.OfModule(moduleId(from)),
        kind = RelationKind.DEPENDS_ON,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfModule(moduleId(to)), Certainty.EXACT),
        evidenceClass = EvidenceClass.BUILD_MODEL,
        site = null,
        scope = Scope(),
    )

    /** [appAttributes] and [libAttributes] are build facts of the modules; [flavors] make variants per build type. */
    fun standard(
        appNamespace: String? = "sample.droid.app",
        withDependencies: Boolean = true,
        appAttributes: Map<String, String> = emptyMap(),
        flavors: List<String> = emptyList(),
        libAttributes: Map<String, String> = emptyMap(),
    ): BuildModel {
        val variants = appVariants(flavors)
        val debug = variants.filter { it.buildType == "debug" }
        return BuildModel(
            buildRoots = listOf(BuildRoot(buildRoot, repository, RepoPath(""), BuildSystem.GRADLE)),
            modules = listOf(
                module(":app", appNamespace, appAttributes),
                module(":lib", "sample.droid.lib", libAttributes),
                module(":util", "sample.droid.util"),
            ),
            targets = listOf(":app", ":lib", ":util").map {
                Target(TargetId("repo//$it#target/android"), moduleId(it), "android", Platform.ANDROID)
            },
            variants = variants,
            sourceSets = listOf(
                sourceSet(":app", "main", variants),
                sourceSet(":app", "debug", debug),
                sourceSet(":lib", "main", emptyList()),
                sourceSet(":util", "main", emptyList()),
            ),
            dependencies = if (withDependencies) {
                listOf(dependency(":app", ":lib"), dependency(":app", ":util"))
            } else {
                emptyList()
            },
        )
    }
}
