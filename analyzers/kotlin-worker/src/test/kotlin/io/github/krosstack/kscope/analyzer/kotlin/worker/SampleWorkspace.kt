package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

/** Builds worker requests from the sample trees under `src/test/resources/samples`. */
object SampleWorkspace {
    private const val REPOSITORY = "sample"

    val samplesRoot: Path = Path.of(
        checkNotNull(SampleWorkspace::class.java.getResource("/samples")) { "The samples are missing" }.toURI(),
    )

    /** The signature-only stand-in for the generated Android resource class that the `generated` sample uses. */
    fun androidResourceStub() = SyntheticSource(
        "gen/R.kt",
        "kotlin",
        "package gen\n\nobject R {\n    object string {\n        const val app_name: Int = 0\n    }\n}\n",
        SourceSetId("replaced-by-the-source-set"),
        "android.resources",
    )

    fun file(relativePath: String): WorkerFile {
        val path = samplesRoot.resolve(relativePath)
        return WorkerFile(artifactRef(path), path.toString(), contentHash(path))
    }

    fun sourceSet(
        gradleModule: String,
        name: String,
        directory: String,
        emitFactsFor: Boolean = true,
        syntheticSources: List<SyntheticSource> = emptyList(),
    ): WorkerSourceSet {
        val module = ModuleId("$REPOSITORY//:$gradleModule")
        val id = SourceSetId("${module.value}@$name")
        return WorkerSourceSet(
            sourceSet = id,
            module = module,
            files = sourceFilesIn(directory),
            syntheticSources = syntheticSources.map { it.copy(sourceSet = id) },
            emitFactsFor = emitFactsFor,
            fingerprint = Fingerprint(sha256(id.value)),
        )
    }

    fun module(name: String, platform: Platform, vararg sourceSets: WorkerSourceSet): WorkerModule = WorkerModule(
        name = name,
        platform = platform,
        sourceSets = sourceSets.toList(),
        dependsOn = emptyList(),
        dependencies = emptyList(),
        friends = emptyList(),
        classpath = emptyList(),
    )

    fun singleModule(gradleModule: String, directory: String, platform: Platform = Platform.JVM): WorkerModule =
        module(gradleModule, platform, sourceSet(gradleModule, "main", directory))

    private fun sourceFilesIn(directory: String): List<WorkerFile> =
        Files.walk(samplesRoot.resolve(directory)).use { paths ->
            paths.filter { Files.isRegularFile(it) && isSource(it) }
                .sorted()
                .map { WorkerFile(artifactRef(it), it.toString(), contentHash(it)) }
                .toList()
        }

    private fun artifactRef(path: Path) = ArtifactRef(
        RepositoryId(REPOSITORY),
        RepoPath(samplesRoot.relativize(path).invariantSeparatorsPathString),
    )

    private fun contentHash(path: Path) = ContentHash(sha256(Files.readAllBytes(path)))

    private fun sha256(text: String) = sha256(text.toByteArray())

    private fun sha256(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun isSource(path: Path) = path.name.endsWith(".kt") || path.name.endsWith(".java")
}
