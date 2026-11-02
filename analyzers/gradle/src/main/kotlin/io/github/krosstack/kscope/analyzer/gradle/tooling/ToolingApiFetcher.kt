package io.github.krosstack.kscope.analyzer.gradle.tooling

import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * What to run Gradle with: the project directory, the plugin jar and the init script that applies it, and the JDK to
 * run Gradle on, or null for the one the build declares or, failing that, the JVM of the tool.
 */
internal class FetchRequest(
    val projectDirectory: Path,
    val pluginJar: Path,
    val initScript: Path,
    val javaHome: Path? = null,
)

internal sealed interface FetchOutcome {
    /** [json] is the array of project documents. */
    class Fetched(val json: String) : FetchOutcome

    class Rejected(val message: String) : FetchOutcome
}

internal fun interface ModelFetcher {
    fun fetch(request: FetchRequest): FetchOutcome
}

/**
 * Fetches the model through the Gradle Tooling API with the Gradle distribution the build itself declares. The
 * daemon is started with the JVM arguments of the build, and the init script is inert without its property. Nothing is
 * written to standard output: the progress of a distribution download goes to standard error.
 */
internal class ToolingApiFetcher(private val timeoutMinutes: Long = DEFAULT_TIMEOUT_MINUTES) : ModelFetcher {
    override fun fetch(request: FetchRequest): FetchOutcome = when {
        request.javaHome != null && !Files.isDirectory(request.javaHome) ->
            FetchOutcome.Rejected(noJavaHomeMessage(request.javaHome))

        !Files.isRegularFile(request.projectDirectory.resolve(WRAPPER_PROPERTIES)) ->
            FetchOutcome.Rejected(noWrapperMessage(request.projectDirectory))

        else -> runBuildAction(request)
    }

    private fun runBuildAction(request: FetchRequest): FetchOutcome {
        val cancellation = GradleConnector.newCancellationTokenSource()
        val watchdog = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "kscope-gradle-watchdog").apply { isDaemon = true }
        }
        watchdog.schedule({ cancellation.cancel() }, timeoutMinutes, TimeUnit.MINUTES)
        val connector = GradleConnector.newConnector()
            .forProjectDirectory(request.projectDirectory.toFile())
            .useBuildDistribution()
        try {
            return StandardOutputDiversion.toStandardError { fetched(connector, request, cancellation) }
        } catch (failure: GradleConnectionException) {
            return FetchOutcome.Rejected(describe(failure))
        } finally {
            watchdog.shutdownNow()
            connector.disconnect()
        }
    }

    private fun fetched(connector: GradleConnector, request: FetchRequest, cancellation: CancellationTokenSource) =
        connector.connect().use { connection ->
            val json = connection.action(FetchProjectModelsAction())
                .withCancellationToken(cancellation.token())
                .withArguments(
                    "--init-script",
                    request.initScript.toString(),
                    "-D$ACTIVATION_PROPERTY=${request.pluginJar}",
                )
                .setStandardOutput(OutputStream.nullOutputStream())
                .setStandardError(OutputStream.nullOutputStream())
                .apply { request.javaHome?.let { setJavaHome(it.toFile()) } }
                .run()
            FetchOutcome.Fetched(json)
        }

    private fun noWrapperMessage(directory: Path): String {
        val subject = "The build in '${directory.fileName}' declares no Gradle distribution"
        return "$subject ($WRAPPER_PROPERTIES is missing), and only the distribution that a build declares is used."
    }

    private fun noJavaHomeMessage(javaHome: Path): String =
        "${GradleJavaHome.VARIABLE} names '$javaHome', which is not a directory; it must be the home of a JDK."

    private fun describe(failure: Throwable): String {
        val messages = generateSequence(failure) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .toList()
        return messages.joinToString(" | ").take(MAX_MESSAGE)
    }

    private companion object {
        const val WRAPPER_PROPERTIES = "gradle/wrapper/gradle-wrapper.properties"
        const val ACTIVATION_PROPERTY = "kscope.gradle.model.pluginJar"
        const val DEFAULT_TIMEOUT_MINUTES = 15L
        const val MAX_MESSAGE = 2_000
    }
}
