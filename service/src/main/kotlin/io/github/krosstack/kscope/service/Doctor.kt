package io.github.krosstack.kscope.service

import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider

@Serializable
public data class DoctorCheck(public val name: String, public val available: Boolean, public val detail: String)

@Serializable
public data class DoctorResult(public val checks: List<DoctorCheck>, public val diagnostics: List<String>)

public fun KrosScope.doctor(): DoctorResult {
    val checks = listOf(
        DoctorCheck(
            "jdk",
            Runtime.version().feature() >= REQUIRED_JAVA && ToolProvider.getSystemJavaCompiler() != null,
            "Java ${Runtime.version().feature()}; JDK 17 or newer required",
        ),
        DoctorCheck("worker", workerAvailable(), "Kotlin worker classpath or installation"),
        DoctorCheck("git", gitAvailable(), "Git executable"),
        DoctorCheck("index", info().indexed, "Workspace index"),
        DoctorCheck("android-sdk", androidSdkAvailable(), "Android SDK directory"),
    )
    return DoctorResult(checks, checks.filterNot { it.available }.map { "${it.name}: unavailable" })
}

private fun workerAvailable(): Boolean {
    val classpath = System.getProperty("kscope.worker.kotlin.classpath")?.takeIf { it.isNotBlank() }
    val home = System.getProperty("kscope.home") ?: System.getenv("KSCOPE_HOME")
    val installed = home?.let { Path.of(it) } ?: KrosScope::class.java.protectionDomain?.codeSource?.location
        ?.toURI()?.let { Path.of(it) }?.takeIf(Files::isRegularFile)?.parent?.parent
    return if (classpath != null) {
        classpath.split(File.pathSeparator).all { it.isNotBlank() && Files.exists(Path.of(it.removeSuffix("*"))) }
    } else {
        installed?.resolve("workers/kotlin/lib")?.let(Files::isDirectory) == true
    }
}

private fun androidSdkAvailable(): Boolean {
    val configured = listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
        .filter { it.isNotBlank() }.map(Path::of)
    val home = Path.of(System.getProperty("user.home"))
    return (configured + listOf(home.resolve("AppData/Local/Android/Sdk"), home.resolve("Android/Sdk")))
        .any(Files::isDirectory)
}

private fun gitAvailable(): Boolean = try {
    val process = ProcessBuilder("git", "--version").redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!completed) process.destroy()
    completed && process.exitValue() == 0
} catch (_: IOException) {
    false
} catch (_: InterruptedException) {
    Thread.currentThread().interrupt()
    false
}

private const val REQUIRED_JAVA = 17
private const val GIT_TIMEOUT_SECONDS = 5L
