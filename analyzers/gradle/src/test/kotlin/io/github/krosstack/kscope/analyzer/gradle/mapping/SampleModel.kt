package io.github.krosstack.kscope.analyzer.gradle.mapping

import io.github.krosstack.kscope.analyzer.gradle.ModelDocuments
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.BuildModel
import java.io.File
import java.nio.file.Path

/** Project documents recorded from Gradle builds, mapped as the provider would map them. */
internal class SampleModel(val mapped: MappedModel) {
    val model: BuildModel get() = mapped.model

    fun module(path: String): Module = model.modules.single { it.path == path && it.buildRoot.value == "tasktide//" }

    fun sourceSets(modulePath: String): Map<String, SourceSet> =
        model.sourceSets.filter { it.module == module(modulePath).id }.associateBy { it.name }

    fun dependsOn(from: String): List<Relationship> = model.dependencies.filter {
        it.kind == RelationKind.DEPENDS_ON && (it.from as? NodeRef.OfModule)?.id == module(from).id
    }

    /** The modules a module depends on, as pairs of module path and declaring configuration. */
    fun moduleDependencies(from: String): List<Pair<String, String?>> = dependsOn(from).mapNotNull { edge ->
        val target = (edge.resolution as? Resolution.Resolved)?.target as? NodeRef.OfModule
        target?.let { it.id.value.removePrefix("tasktide//") to edge.attributes["configuration"] }
    }.sortedBy { it.first + it.second }

    fun externalDependencies(from: String): List<NodeRef.OfExternal> =
        dependsOn(from).mapNotNull { (it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfExternal }

    fun classpath(sourceSetId: String): List<String> =
        mapped.classpaths.getValue(model.sourceSets.single { it.id.value == "tasktide//$sourceSetId" }.id)

    companion object {
        private val workspace = Path.of("/work/tasktide")

        fun load(
            name: String,
            workspaceRoot: Path = workspace,
            repository: String = "tasktide",
            edit: (String) -> String = { it },
        ): SampleModel {
            val root = workspaceRoot.toAbsolutePath().normalize().toString().replace(File.separatorChar, '/')
            val text = checkNotNull(SampleModel::class.java.getResource("/models/$name")) { "Missing sample $name" }
                .readText()
                .replace("%ROOT%", root)
                .let(edit)
            val mapper = BuildModelMapper(RepositoryId(repository), WorkspacePaths(workspaceRoot))
            return SampleModel(mapper.map(ModelDocuments.parse(text)))
        }
    }
}
