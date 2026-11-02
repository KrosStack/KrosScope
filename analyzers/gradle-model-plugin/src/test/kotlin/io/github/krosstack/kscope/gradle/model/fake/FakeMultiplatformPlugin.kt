package io.github.krosstack.kscope.gradle.model.fake

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Stands in for the Kotlin Multiplatform plugin under its id. */
class FakeMultiplatformPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}
