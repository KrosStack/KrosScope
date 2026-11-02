package io.github.krosstack.kscope.analyzer.gradle.statics

import io.github.krosstack.kscope.analyzer.gradle.GradleStaticProvider
import io.github.krosstack.kscope.analyzer.gradle.WorkspaceRequests
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.ScriptOutliner
import io.github.krosstack.kscope.spi.WorkspaceFiles
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertIs

/** Reads the outline that was generated for a Kotlin script and checked in beside it as `<script>.outline.json`. */
internal object CheckedInOutlines : ScriptOutliner {
    override fun outline(scripts: List<Artifact>, files: WorkspaceFiles): Map<ArtifactRef, ScriptOutline> =
        scripts.mapNotNull { script ->
            val path = files.absolutePath(script.ref)
            val outline = path.resolveSibling(path.fileName.toString() + ".outline.json")
            if (Files.isRegularFile(outline)) {
                script.ref to KscopeJson.decodeFromString(ScriptOutline.serializer(), Files.readString(outline))
            } else {
                null
            }
        }.toMap()
}

/** A copy of a checked-in build tree in a temporary directory, which a test may add to. */
internal class StaticWorkspace(tree: String) : AutoCloseable {
    private val base: Path = Files.createTempDirectory("kscope-static")
    val root: Path = base.resolve("space")

    init {
        val source = Path.of(checkNotNull(StaticWorkspace::class.java.getResource("/static/$tree")).toURI())
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val target = root.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target)
            }
        }
    }

    fun write(path: String, text: String = "") {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    fun directory(path: String) {
        Files.createDirectories(root.resolve(path))
    }

    fun provided(kotlinScripts: ScriptOutliner = CheckedInOutlines): BuildModelResult.Provided =
        assertIs<BuildModelResult.Provided>(provide(kotlinScripts))

    fun provide(kotlinScripts: ScriptOutliner = CheckedInOutlines): BuildModelResult =
        GradleStaticProvider(kotlinScripts).provide(WorkspaceRequests.of(root, trusted = false))

    override fun close() {
        base.toFile().deleteRecursively()
    }
}

internal const val ROOT = "tasktide//"

internal fun BuildModelResult.Provided.module(path: String, root: String = ROOT): Module =
    model.modules.single { it.id.value == root + path }

internal fun BuildModelResult.Provided.sourceSets(path: String, root: String = ROOT): Map<String, SourceSet> =
    model.sourceSets.filter { it.module.value == root + path }.associateBy { it.name }.toSortedMap()

internal fun BuildModelResult.Provided.edgesOf(path: String, root: String = ROOT): List<Relationship> =
    model.dependencies.filter { (it.from as? NodeRef.OfModule)?.id?.value == root + path }

internal fun BuildModelResult.Provided.problems(code: String): List<Diagnostic> =
    diagnostics.filter { it.code.value == code }

internal fun BuildModelResult.Provided.variantsOf(path: String, sourceSet: String): List<String> =
    sourceSets(path).getValue(sourceSet).variants.map { it.value.substringAfter("#variant/") }

internal fun BuildModelResult.Provided.dependsOnOf(path: String): Map<String, List<String>> =
    sourceSets(path).mapValues { (_, sourceSet) -> sourceSet.dependsOn.map { it.value.substringAfter('@') } }
