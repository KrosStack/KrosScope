package io.github.krosstack.kscope.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class VerifyModuleDependencies : DefaultTask() {
    @get:Input
    abstract val modulePath: Property<String>

    @get:Input
    abstract val declaredProjectDependencies: SetProperty<String>

    @get:Input
    abstract val runtimeModules: SetProperty<String>

    init {
        outputs.upToDateWhen { true }
    }

    @TaskAction
    fun verify() {
        ModuleDependencyRules.confinements.forEach { (library, allowedProject) ->
            check(modulePath.get() == allowedProject || library !in runtimeModules.get()) {
                "$library must not appear on ${modulePath.get()}'s runtime classpath; confined to $allowedProject"
            }
        }
        val violations = ModuleDependencyRules.violations(modulePath.get(), declaredProjectDependencies.get())
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module dependency rules violated (see docs/architecture.md):\n" +
                    violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}
