package io.github.krosstack.kscope.analyzer.kotlin.worker.aa

import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.KtModuleProviderBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The binary modules of one session: the request's classpath entries, plus the worker's own Kotlin standard library
 * and, for JVM and Android modules, the running JDK as defaults. Only those two platforms read Java classes: the
 * session of any other platform has no Java support, and the standard library aliases that expand to JDK classes
 * (`HashMap`, `ArrayList`) would send it into a Java scope. A library is created once and shared by every module that
 * lists it.
 */
internal class LibraryModules(private val provider: KtModuleProviderBuilder) {
    private val classpathLibraries = mutableMapOf<Path, KaLibraryModule>()

    private val jdk: KaLibraryModule by lazy {
        provider.buildKtSdkModule {
            platform = JvmPlatforms.defaultJvmPlatform
            libraryName = "jdk"
            addBinaryRootsFromJdkHome(Path.of(System.getProperty("java.home")), false)
        }
    }

    private val defaultStdlib: KaLibraryModule? by lazy { WorkerStdlib.jar()?.let(::library) }

    fun forModule(module: WorkerModule): List<KaLibraryModule> {
        val entries = module.classpath.mapNotNull(::existingEntry)
        val stdlib = if (entries.any(::isStdlib)) emptyList() else listOfNotNull(defaultStdlib)
        val sdk = if (module.platform.readsJavaClasses()) listOf(jdk) else emptyList()
        return entries.map { classpathLibraries.getOrPut(it) { library(it) } } + stdlib + sdk
    }

    private fun Platform.readsJavaClasses(): Boolean = this == Platform.JVM || this == Platform.ANDROID

    private fun library(root: Path): KaLibraryModule = provider.buildKtLibraryModule {
        platform = JvmPlatforms.defaultJvmPlatform
        libraryName = root.fileName?.toString() ?: root.toString()
        addBinaryRoot(root)
    }

    private fun existingEntry(entry: String): Path? = try {
        Path.of(entry).takeIf { Files.exists(it) }
    } catch (_: InvalidPathException) {
        null
    }

    private fun isStdlib(entry: Path): Boolean = entry.fileName?.toString()?.startsWith("kotlin-stdlib") == true
}

internal object WorkerStdlib {
    fun jar(): Path? = try {
        Unit::class.java.protectionDomain?.codeSource?.location?.toURI()?.let(Path::of)
    } catch (_: java.net.URISyntaxException) {
        null
    }
}
