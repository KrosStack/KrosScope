package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.WorkerEnvironment
import io.github.krosstack.kscope.analyzer.kotlin.WorkerSettings
import java.io.File
import java.net.URISyntaxException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.Path

internal sealed interface WorkerCommand {
    class Runnable(val arguments: List<String>) : WorkerCommand

    class Missing(val reason: String) : WorkerCommand
}

/**
 * Finds the worker to start. Its classpath is the system property `kscope.worker.kotlin.classpath` or else the
 * libraries in `<kscope home>/workers/kotlin/lib`; the home is the system property `kscope.home`, the environment
 * variable `KSCOPE_HOME`, or the directory above the `lib` directory of the running application jar. The worker heap
 * is the environment variable `KSCOPE_WORKER_HEAP`, else the heap that the settings of the workspace set, else
 * `-Xmx2g`.
 */
internal class WorkerLocator(
    private val property: (String) -> String?,
    private val environment: (String) -> String?,
    private val applicationJar: () -> Path?,
) {
    fun command(settings: WorkerSettings = WorkerSettings.NONE): WorkerCommand {
        val classpath = explicitClasspath() ?: homeClasspath()
        return if (classpath == null) {
            WorkerCommand.Missing(MISSING)
        } else {
            WorkerCommand.Runnable(launch(classpath, settings))
        }
    }

    private fun launch(classpath: String, settings: WorkerSettings): List<String> = listOf(
        Path.of(property(JAVA_HOME).orEmpty(), "bin", "java").toString(),
        "-Xmx${heapSetting(environment, settings.heapMebibytes)}",
        "-XX:+DisplayVMOutputToStderr",
        "-cp",
        classpath,
        WORKER_MAIN_CLASS,
    )

    private fun explicitClasspath(): String? = property(CLASSPATH_PROPERTY)?.takeIf { it.isNotBlank() }

    private fun homeClasspath(): String? = home()?.resolve(WORKER_LIBRARIES)?.takeIf(Files::isDirectory)
        ?.let { "$it${File.separator}*" }

    private fun home(): Path? =
        (property(HOME_PROPERTY) ?: environment(HOME_VARIABLE))?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: homeOfJar(applicationJar())

    private fun homeOfJar(jar: Path?): Path? {
        val libraries = jar?.takeIf(Files::isRegularFile)?.parent
        return libraries?.takeIf { it.fileName?.toString() == LIBRARY_DIRECTORY }?.parent
    }

    companion object {
        const val CLASSPATH_PROPERTY = "kscope.worker.kotlin.classpath"
        const val HOME_PROPERTY = "kscope.home"
        const val HOME_VARIABLE = "KSCOPE_HOME"
        const val HEAP_VARIABLE = WorkerEnvironment.HEAP_VARIABLE
        const val WORKER_MAIN_CLASS = "io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerMain"

        private const val JAVA_HOME = "java.home"
        private const val DEFAULT_HEAP = "2g"
        private const val LIBRARY_DIRECTORY = "lib"
        private const val WORKER_LIBRARIES = "workers/kotlin/lib"
        private const val MISSING = "it is not installed: set the system property $CLASSPATH_PROPERTY, or install " +
            "it in workers/kotlin/lib of the kscope home (system property $HOME_PROPERTY or variable $HOME_VARIABLE)"

        fun heapSetting(environment: (String) -> String?, configuredMebibytes: Int? = null): String =
            WorkerEnvironment.heapOption(environment) ?: configuredMebibytes?.let { "${it}m" } ?: DEFAULT_HEAP

        fun system(): WorkerLocator = WorkerLocator(
            property = { System.getProperty(it) },
            environment = { System.getenv(it) },
            applicationJar = ::runningJar,
        )

        private fun runningJar(): Path? = try {
            WorkerLocator::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { Path.of(it) }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: FileSystemNotFoundException) {
            null
        }
    }
}
