package io.github.krosstack.kscope.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyFileLength : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val maxLines: Property<Int>

    init {
        outputs.upToDateWhen { true }
    }

    @TaskAction
    fun verify() {
        val limit = maxLines.get()
        val tooLong = mutableListOf<String>()
        sources.asFileTree.matching { include("**/*.kt") }.visit {
            if (!isDirectory && file.useLines { it.count() } > limit) tooLong += path
        }
        if (tooLong.isNotEmpty()) {
            throw GradleException(
                "Files longer than $limit lines (see docs/code-guidelines.md):\n" +
                    tooLong.sorted().joinToString("\n") { "  - $it" },
            )
        }
    }
}
