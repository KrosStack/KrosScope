package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

private const val HASH_LENGTH = 64

private class SourceSetDirectory(val gradlePath: String, val name: String, val files: List<WorkerFile>) {
    val isCommon = name.startsWith("common")
    val isTest = name.contains("Test", ignoreCase = true) || name == "test"
    val moduleName = "$gradlePath@$name"
    val isLibraryCode = !isTest && name in setOf("commonMain", "desktopMain", "main")
}

/**
 * A handcrafted analysis layout for the reference workspace: one module per source set directory, the Gradle modules in
 * the order of their real dependencies, and every module depending on all earlier ones it could see (common code on
 * common code, JVM code on common and JVM main code). Tests and flavours see the main code of their own Gradle module
 * as a friend. No library but the standard library and the JDK is on any classpath.
 */
object ReferenceWorkspaceLayout {
    private val repositoryRoot: Path = Path.of("").toAbsolutePath().parent.parent
    val workspaceRoot: Path = repositoryRoot.resolve("fixtures").resolve("reference-workspace")

    private val gradleOrder = listOf(
        ":core:model", ":core:legacy", ":data:db", ":data:settings", ":native:bridge", ":ui:shared",
        ":sync:protocol", ":sync:engine", ":sync:service", ":feature:settings", ":feature:tasks", ":desktop", ":app",
    )
    private val setOrder = listOf(
        "commonMain", "desktopMain", "androidMain", "main", "commonTest", "desktopTest", "androidTest", "test",
        "debug", "demo", "full",
    )

    fun modules(): List<WorkerModule> {
        val directories = sourceSetDirectories()
            .sortedBy { rankOf(gradleOrder, it.gradlePath) * (setOrder.size + 1) + rankOf(setOrder, it.name) }
        return directories.mapIndexed { index, directory -> moduleOf(directory, directories.take(index), directories) }
    }

    private fun rankOf(order: List<String>, name: String): Int = order.indexOf(name).takeIf { it >= 0 } ?: order.size

    fun fileCount(): Int = modules().sumOf { module -> module.sourceSets.sumOf { it.files.size } }

    private fun moduleOf(
        directory: SourceSetDirectory,
        earlier: List<SourceSetDirectory>,
        all: List<SourceSetDirectory>,
    ): WorkerModule {
        val module = ModuleId("tasktide//${directory.gradlePath}")
        val sourceSet = WorkerSourceSet(
            SourceSetId("${module.value}@${directory.name}"),
            module,
            directory.files,
            emptyList(),
            true,
            Fingerprint("0".repeat(HASH_LENGTH)),
        )
        val own = all.filter { it.gradlePath == directory.gradlePath && it !== directory }
        val friends = own.filter { friendOf(directory, it) }
        val refined = if (directory.name == "commonMain") emptyList() else own.filter { refines(directory, it) }
        return WorkerModule(
            name = directory.moduleName,
            platform = if (directory.isCommon) Platform.COMMON else Platform.JVM,
            sourceSets = listOf(sourceSet),
            dependsOn = refined.map { it.moduleName },
            dependencies = (earlier.filter { canSee(directory, it) } + friends).map { it.moduleName }.distinct(),
            friends = friends.map { it.moduleName },
            classpath = emptyList(),
        )
    }

    private fun friendOf(directory: SourceSetDirectory, candidate: SourceSetDirectory): Boolean {
        val isMainCode = if (directory.isTest) candidate.isLibraryCode else candidate.name == "main"
        return isMainCode && (candidate.isCommon || !directory.isCommon)
    }

    private fun refines(directory: SourceSetDirectory, other: SourceSetDirectory): Boolean {
        val refinesMain = directory.name in setOf("desktopMain", "androidMain") && other.name == "commonMain"
        val refinesTest = directory.name == "desktopTest" && other.name == "commonTest"
        return refinesMain || refinesTest
    }

    private fun canSee(from: SourceSetDirectory, candidate: SourceSetDirectory): Boolean = when {
        candidate.isTest -> false
        candidate.gradlePath == from.gradlePath -> false
        from.isCommon -> candidate.isCommon
        else -> candidate.isCommon || candidate.isLibraryCode
    }

    private fun sourceSetDirectories(): List<SourceSetDirectory> = Files.walk(workspaceRoot).use { paths ->
        paths.filter { Files.isDirectory(it) && it.parent?.name == "src" && "build" !in relativePath(it).split('/') }
            .sorted().toList()
    }.mapNotNull(::directoryOf)

    private fun directoryOf(directory: Path): SourceSetDirectory? {
        val files = filesOf(directory)
        val gradlePath = ":" + relativePath(directory.parent.parent).replace('/', ':')
        return if (files.isEmpty()) null else SourceSetDirectory(gradlePath, directory.name, files)
    }

    private fun relativePath(path: Path) = workspaceRoot.relativize(path).invariantSeparatorsPathString

    private fun filesOf(directory: Path): List<WorkerFile> = Files.walk(directory).use { paths ->
        paths.filter { Files.isRegularFile(it) && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
            .sorted().toList()
    }.map {
        val repositoryRelative = repositoryRoot.relativize(it).invariantSeparatorsPathString
        WorkerFile(
            ArtifactRef(RepositoryId("tasktide"), RepoPath(repositoryRelative)),
            it.toString(),
            ContentHash("0".repeat(HASH_LENGTH)),
        )
    }
}
