package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.DoctorCheck
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.InstalledComponent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal class WorkerSelfTest(
    private val directory: ComponentDirectory,
    private val manifest: ComponentManifest = ComponentManifest.shipped(),
) {
    internal fun check(): DoctorCheck? {
        val checks = directory.trySemanticLock(LOCK_TIMEOUT_MILLIS) { listOfNotNull(run()) }
        return if (checks == null) {
            DoctorCheck("semantic-worker", false, "Self-test skipped: component busy")
        } else {
            checks.firstOrNull()
        }
    }

    internal fun run(preferred: InstalledComponent? = null): DoctorCheck? = directory.exclusively("semantic") {
        val installed = listOfNotNull(preferred) + directory.installed()
            .filter { it.directory.fileName.toString() == it.version }
        val worker = manifest.installed("semantic", installed)
            ?: return@exclusively null
        modelDirectory(installed)?.let {
            val jdkHome = Path.of(System.getProperty("java.home"))
            val problem = try {
                provider(worker.directory, it, jdkHome).use { provider ->
                    provider.embedQuery("self-test")
                }
                null
            } catch (failure: IOException) {
                failureText(failure.message)
            } catch (failure: IllegalArgumentException) {
                failureText(failure.message)
            } catch (failure: IllegalStateException) {
                failureText(failure.message)
            }
            val marker = worker.directory.resolve(PROBLEM_FILE)
            if (problem == null) {
                Files.deleteIfExists(marker)
            } else {
                writeProblem(marker, problem)
            }
            DoctorCheck("semantic-worker", problem == null, problem ?: "Semantic worker answered.")
        }
    }

    private fun provider(component: Path, model: Path, jdkHome: Path): WorkerEmbeddingProvider {
        val layout = runCatching {
            ComponentLayout(component.toAbsolutePath(), model.toAbsolutePath(), jdkHome).let {
                it.identity to it.command()
            }
        }.getOrElse { failure ->
            when (failure) {
                is IOException, is IllegalArgumentException, is IllegalStateException ->
                    throw IOException(redact(failure.message.orEmpty(), jdkHome), failure)

                else -> throw failure
            }
        }
        return WorkerEmbeddingProvider(layout.first, layout.second, redact = { redact(it, jdkHome) })
    }

    private fun failureText(message: String?): String = "$FAILURE_PREFIX $message. $JDK_REQUIREMENT"

    private fun redact(message: String, jdkHome: Path): String {
        val paths = listOf(directory.root.toAbsolutePath() to COMPONENTS_TOKEN, jdkHome.toAbsolutePath() to JDK_TOKEN)
        return paths.sortedByDescending { it.first.toString().length }
            .fold(message) { text, (path, token) ->
                val forward = path.toString().replace('\\', '/').trimEnd('/')
                text.replace(forward, token).replace(forward.replace('/', '\\'), token)
            }
    }

    private fun writeProblem(marker: Path, problem: String) {
        val temporary = Files.createTempFile(marker.parent, ".self-test-", ".tmp")
        try {
            Files.writeString(temporary, problem)
            Files.move(temporary, marker, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun modelDirectory(installed: List<InstalledComponent>): Path? =
        manifest.installed("semantic-model", installed)?.directory?.let {
            it.resolve("model").takeIf { nested -> Files.exists(nested, NOFOLLOW_LINKS) } ?: it
        }

    internal companion object {
        private const val COMPONENTS_TOKEN = "<components>"
        private const val JDK_TOKEN = "<jdk>"
        internal const val LOCK_TIMEOUT_MILLIS = 3000L
        internal const val PROBLEM_FILE = "kscope-self-test.txt"
        internal const val FAILURE_PREFIX = "Self-test failed:"
        internal const val JDK_REQUIREMENT = "Requires a JDK 17 whose bundled Microsoft Visual C++ runtime is " +
            "14.40 or newer, for example Temurin 17.0.21 or newer."
    }
}
