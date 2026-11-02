package io.github.krosstack.kscope.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyPackageSize : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val moduleDirectory: DirectoryProperty

    @get:Input
    abstract val maxFiles: Property<Int>

    @get:Input
    abstract val directoryLimits: MapProperty<String, Int>

    init {
        outputs.upToDateWhen { true }
    }

    @TaskAction
    fun verify() {
        val module = moduleDirectory.get().asFile
        val kotlinFilesPerDirectory = sources.asFileTree.matching { include("**/*.kt") }.files
            .groupingBy { it.parentFile.relativeTo(module).invariantSeparatorsPath }
            .eachCount()
        val violations = PackageSizeLimits(maxFiles.get(), directoryLimits.get()).violations(kotlinFilesPerDirectory)
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Directories with too many Kotlin files (see docs/code-guidelines.md):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}
