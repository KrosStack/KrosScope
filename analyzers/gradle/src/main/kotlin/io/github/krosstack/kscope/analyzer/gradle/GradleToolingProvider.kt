package io.github.krosstack.kscope.analyzer.gradle

import io.github.krosstack.kscope.analyzer.gradle.mapping.BuildModelMapper
import io.github.krosstack.kscope.analyzer.gradle.tooling.BuildInputs
import io.github.krosstack.kscope.analyzer.gradle.tooling.BuildRootFetcher
import io.github.krosstack.kscope.analyzer.gradle.tooling.FetchOutcome
import io.github.krosstack.kscope.analyzer.gradle.tooling.GradleJavaHome
import io.github.krosstack.kscope.analyzer.gradle.tooling.ModelCache
import io.github.krosstack.kscope.analyzer.gradle.tooling.ModelFetcher
import io.github.krosstack.kscope.analyzer.gradle.tooling.PluginAssets
import io.github.krosstack.kscope.analyzer.gradle.tooling.ToolingApiFetcher
import io.github.krosstack.kscope.analyzer.gradle.workspace.ConventionRoots
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceDisk
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspacePaths
import io.github.krosstack.kscope.analyzer.gradle.workspace.WorkspaceRoot
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Path

/**
 * Reads the exact build model by running Gradle: the Tooling API configures the build with the Gradle distribution
 * it declares, and a thin plugin injected through an init script reports the projects, source sets, variants,
 * dependencies and compile classpaths. This executes the build logic of the workspace, so it runs only for a trusted
 * workspace. The documents are cached below [indexDirectory] under the fingerprint of the build inputs.
 *
 * Gradle reports no file contents, so the facts that are read from files, such as the version catalog symbols and the
 * references to them, come from [factsFrom] (the static provider) and are the same whichever provider supplies the
 * model.
 */
public class GradleToolingProvider internal constructor(
    private val indexDirectory: Path,
    private val factsFrom: BuildModelProvider? = null,
    private val javaHome: Path? = null,
    private val fetcher: ModelFetcher,
) : BuildModelProvider {
    public constructor(indexDirectory: Path, factsFrom: BuildModelProvider? = null) :
        this(indexDirectory, factsFrom, GradleJavaHome.fromEnvironment(), ToolingApiFetcher())

    override val producer: ProducerRef = ProducerRef("kscope.build.gradle.tooling", "1")

    override val requiresTrust: Boolean = true

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val settings = request.artifacts.filter { it.type.id == ArtifactTypes.GRADLE_SETTINGS }
        if (!request.trusted || settings.isEmpty()) return BuildModelResult.NotApplicable
        return try {
            fetchAndMap(request, settings)
        } catch (failure: IOException) {
            failed(IO_FAILED, "The Gradle model could not be stored or read: ${failure.message}")
        } catch (failure: SerializationException) {
            failed(INVALID_MODEL, "The Gradle model could not be understood: ${failure.message}")
        }
    }

    private fun fetchAndMap(request: BuildModelRequest, settings: List<Artifact>): BuildModelResult {
        val root = WorkspaceRoot.of(request.files, settings.first())
        val gradleModelDirectory = indexDirectory.resolve(DIRECTORY)
        val assets = PluginAssets.extract(gradleModelDirectory)
        val inputs = BuildInputs(request.repository.id, root, gradleUserHome(), javaHome)
        val key = inputs.fingerprint(producer, request.artifacts, assets)
        val cache = ModelCache(gradleModelDirectory)
        val cached = cache.read(key)
        if (cached != null) return mapped(cached, request, root)
        val directories = settings.map { request.files.absolutePath(it.ref).parent }
        return when (val outcome = BuildRootFetcher(fetcher, javaHome).fetch(directories, assets)) {
            is FetchOutcome.Fetched -> mapped(outcome.json.also { cache.write(key, it) }, request, root)
            is FetchOutcome.Rejected -> failed(GRADLE_FAILED, outcome.message)
        }
    }

    private fun mapped(json: String, request: BuildModelRequest, root: Path): BuildModelResult {
        val documents = ModelDocuments.parseFetches(json)
        val unsupported = documents.firstOrNull { it.schema != SUPPORTED_SCHEMA }
        if (unsupported != null) {
            return failed(INVALID_MODEL, "The Gradle model has schema ${unsupported.schema}, not $SUPPORTED_SCHEMA.")
        }
        val mapped = BuildModelMapper(request.repository.id, WorkspacePaths(root)).map(documents)
        val model = ConventionRoots(WorkspaceDisk(root)).supplement(mapped.model)
        val fileFacts = fileFactsOf(request)
        return BuildModelResult.Provided(
            model,
            mapped.diagnostics + fileFacts.diagnostics,
            mapped.classpaths,
            fileFacts.facts,
        )
    }

    private fun fileFactsOf(request: BuildModelRequest): FileFacts = when (val result = factsFrom?.provide(request)) {
        is BuildModelResult.Provided -> FileFacts(result.facts, emptyList())
        is BuildModelResult.Failed -> FileFacts(emptyList(), listOf(unavailableFacts(result)))
        BuildModelResult.NotApplicable, null -> FileFacts(emptyList(), emptyList())
    }

    private fun unavailableFacts(failure: BuildModelResult.Failed) = Diagnostic(
        DiagnosticSeverity.WARNING,
        ReasonCode(FACTS_UNAVAILABLE),
        "The version catalog symbols and their references are missing: ${failure.message}",
        null,
    )

    private fun gradleUserHome(): Path =
        System.getenv("GRADLE_USER_HOME")?.let(Path::of) ?: Path.of(System.getProperty("user.home"), ".gradle")

    private fun failed(code: String, message: String) = BuildModelResult.Failed(ReasonCode(code), message)

    private companion object {
        const val DIRECTORY = "gradle-model"
        const val GRADLE_FAILED = "gradle.tooling.failed"
        const val INVALID_MODEL = "gradle.tooling.invalid-model"
        const val IO_FAILED = "gradle.tooling.io-failed"
        const val FACTS_UNAVAILABLE = "gradle.tooling.file-facts-unavailable"
    }
}

private class FileFacts(val facts: List<Fact>, val diagnostics: List<Diagnostic>)
